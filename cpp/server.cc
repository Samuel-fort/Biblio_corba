#include "bibliotheque.hh"
#include <mysql.h>
#include <mutex>
#include <string>
#include <cstring>
#include <cstdlib>
#include <iostream>
#include <stdexcept>

static const int MAX_EMPRUNTS = 3;

class BiblioImpl : public POA_Biblio::Bibliotheque {
  MYSQL* db_;
  std::mutex mtx_;

  // ---------- utilitaires ----------
  [[noreturn]] void fail() {
    Biblio::ErreurBD e;
    e.message = CORBA::string_dup(mysql_error(db_));
    throw e;
  }
  std::string esc(const char* s) {
    std::string out(strlen(s) * 2 + 1, '\0');
    unsigned long n = mysql_real_escape_string(db_, &out[0], s, strlen(s));
    out.resize(n);
    return "'" + out + "'";
  }
  MYSQL_RES* query(const std::string& sql) {
    if (mysql_query(db_, sql.c_str())) fail();
    return mysql_store_result(db_);          // nullptr pour INSERT/UPDATE
  }
  struct Res {
    MYSQL_RES* r;
    explicit Res(MYSQL_RES* p) : r(p) {}
    ~Res() { if (r) mysql_free_result(r); }
  };
  static const char* S(MYSQL_ROW row, int i) { return row[i] ? row[i] : ""; }

  long scalar(const std::string& sql) {
    Res r(query(sql));
    MYSQL_ROW row = mysql_fetch_row(r.r);
    return row ? atol(S(row, 0)) : 0;
  }
  void exists_or_throw(const char* table, const char* entite, long id) {
    if (scalar("SELECT COUNT(*) FROM " + std::string(table) +
               " WHERE id=" + std::to_string(id) + " FOR UPDATE") == 0) {
      Biblio::Introuvable e;
      e.entite = CORBA::string_dup(entite);
      e.id = (CORBA::Long)id;
      throw e;
    }
  }
  Biblio::ListeLivres* livres_depuis(const std::string& sql) {
    Res r(query(sql));
    Biblio::ListeLivres_var res = new Biblio::ListeLivres;
    res->length((CORBA::ULong)mysql_num_rows(r.r));
    CORBA::ULong i = 0;
    while (MYSQL_ROW row = mysql_fetch_row(r.r)) {
      res[i].id         = atoi(S(row, 0));
      res[i].titre      = CORBA::string_dup(S(row, 1));
      res[i].auteur     = CORBA::string_dup(S(row, 2));
      res[i].isbn       = CORBA::string_dup(S(row, 3));
      res[i].disponible = atoi(S(row, 4)) != 0;
      ++i;
    }
    return res._retn();
  }
  static const char* SELECT_LIVRES() {
    return "SELECT l.id,l.titre,l.auteur,l.isbn,"
           " NOT EXISTS(SELECT 1 FROM emprunt e"
           "            WHERE e.livre_id=l.id AND e.date_retour IS NULL) "
           "FROM livre l ";
  }

public:
  BiblioImpl(const char* host, const char* user,
             const char* pass, const char* dbname) {
    db_ = mysql_init(nullptr);
    if (!mysql_real_connect(db_, host, user, pass, dbname, 0, nullptr, 0))
      throw std::runtime_error(std::string("Connexion MySQL : ") + mysql_error(db_));
    mysql_set_character_set(db_, "utf8mb4");
  }
  ~BiblioImpl() { mysql_close(db_); }

  // ---------- opérations IDL ----------
  CORBA::Long ajouter_livre(const char* titre, const char* auteur,
                            const char* isbn) {
    std::lock_guard<std::mutex> lk(mtx_);
    query("INSERT INTO livre(titre,auteur,isbn) VALUES(" + esc(titre) + "," +
          esc(auteur) + "," + esc(isbn) + ")");
    return (CORBA::Long)mysql_insert_id(db_);
  }

  CORBA::Long ajouter_adherent(const char* nom, const char* email) {
    std::lock_guard<std::mutex> lk(mtx_);
    query("INSERT INTO adherent(nom,email) VALUES(" + esc(nom) + "," +
          esc(email) + ")");
    return (CORBA::Long)mysql_insert_id(db_);
  }

  Biblio::ListeLivres* lister_livres() {
    std::lock_guard<std::mutex> lk(mtx_);
    return livres_depuis(std::string(SELECT_LIVRES()) + "ORDER BY l.titre");
  }

  Biblio::ListeLivres* rechercher(const char* motif) {
    std::lock_guard<std::mutex> lk(mtx_);
    std::string m = esc((std::string("%") + motif + "%").c_str());
    return livres_depuis(std::string(SELECT_LIVRES()) +
                         "WHERE l.titre LIKE " + m +
                         " OR l.auteur LIKE " + m + " ORDER BY l.titre");
  }

  CORBA::Long emprunter(CORBA::Long livre_id, CORBA::Long adherent_id,
                        CORBA::Long jours) {
    std::lock_guard<std::mutex> lk(mtx_);
    query("START TRANSACTION");
    try {
      exists_or_throw("livre", "livre", livre_id);
      exists_or_throw("adherent", "adherent", adherent_id);

      long en_cours = scalar(
        "SELECT COUNT(*) FROM emprunt WHERE adherent_id=" +
        std::to_string(adherent_id) + " AND date_retour IS NULL");
      if (en_cours >= MAX_EMPRUNTS) {
        Biblio::LimiteAtteinte e;
        e.adherent_id = adherent_id;
        e.maximum = MAX_EMPRUNTS;
        throw e;
      }
      if (scalar("SELECT COUNT(*) FROM emprunt WHERE livre_id=" +
                 std::to_string(livre_id) + " AND date_retour IS NULL") > 0) {
        Biblio::LivreIndisponible e;
        e.livre_id = livre_id;
        throw e;
      }
      query("INSERT INTO emprunt(livre_id,adherent_id,date_retour_prevue) "
            "VALUES(" + std::to_string(livre_id) + "," +
            std::to_string(adherent_id) + ",CURDATE() + INTERVAL " +
            std::to_string(jours > 0 ? jours : 14) + " DAY)");
      CORBA::Long id = (CORBA::Long)mysql_insert_id(db_);
      query("COMMIT");
      return id;
    } catch (...) {
      mysql_query(db_, "ROLLBACK");
      throw;
    }
  }

  void retourner(CORBA::Long emprunt_id) {
    std::lock_guard<std::mutex> lk(mtx_);
    query("UPDATE emprunt SET date_retour=NOW() WHERE id=" +
          std::to_string(emprunt_id) + " AND date_retour IS NULL");
    if (mysql_affected_rows(db_) == 0) {
      Biblio::Introuvable e;
      e.entite = CORBA::string_dup("emprunt actif");
      e.id = emprunt_id;
      throw e;
    }
  }

  Biblio::ListeEmprunts* emprunts_en_cours(CORBA::Long adherent_id) {
    std::lock_guard<std::mutex> lk(mtx_);
    Res r(query(
      "SELECT e.id,e.livre_id,l.titre,e.date_emprunt,e.date_retour_prevue "
      "FROM emprunt e JOIN livre l ON l.id=e.livre_id "
      "WHERE e.adherent_id=" + std::to_string(adherent_id) +
      " AND e.date_retour IS NULL ORDER BY e.date_retour_prevue"));
    Biblio::ListeEmprunts_var res = new Biblio::ListeEmprunts;
    res->length((CORBA::ULong)mysql_num_rows(r.r));
    CORBA::ULong i = 0;
    while (MYSQL_ROW row = mysql_fetch_row(r.r)) {
      res[i].id                 = atoi(S(row, 0));
      res[i].livre_id           = atoi(S(row, 1));
      res[i].titre              = CORBA::string_dup(S(row, 2));
      res[i].date_emprunt       = CORBA::string_dup(S(row, 3));
      res[i].date_retour_prevue = CORBA::string_dup(S(row, 4));
      ++i;
    }
    return res._retn();
  }
};

int main(int argc, char** argv) {
  try {
    CORBA::ORB_var orb = CORBA::ORB_init(argc, argv);
    CORBA::Object_var obj = orb->resolve_initial_references("RootPOA");
    PortableServer::POA_var poa = PortableServer::POA::_narrow(obj);

    const char* pass = getenv("DB_PASS") ? getenv("DB_PASS") : "";
    BiblioImpl* servant = new BiblioImpl("127.0.0.1", "biblio", pass, "biblio");

    PortableServer::ObjectId_var oid = poa->activate_object(servant);
    Biblio::Bibliotheque_var ref = servant->_this();
    servant->_remove_ref();

    CORBA::Object_var ns = orb->resolve_initial_references("NameService");
    CosNaming::NamingContext_var root = CosNaming::NamingContext::_narrow(ns);
    CosNaming::Name name;
    name.length(1);
    name[0].id   = CORBA::string_dup("Bibliotheque");
    name[0].kind = CORBA::string_dup("");
    root->rebind(name, ref);

    poa->the_POAManager()->activate();
    std::cout << "Serveur prêt." << std::endl;
    orb->run();
  } catch (CORBA::Exception& e) {
    std::cerr << "Erreur CORBA : " << e._name() << std::endl;
    return 1;
  } catch (std::exception& e) {
    std::cerr << e.what() << std::endl;
    return 1;
  }
  return 0;
}
