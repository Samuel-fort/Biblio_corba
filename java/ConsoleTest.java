import Biblio.*;

/** Scénario de test sans interface graphique. */
public class ConsoleTest {
    public static void main(String[] args) {
        try {
            Bibliotheque b = Connexion.ouvrir(args);
            int l1 = b.ajouter_livre("Notre-Dame de Paris", "Victor Hugo", "978-J1");
            int a1 = b.ajouter_adherent("Jean", "jean@example.com");
            int a2 = b.ajouter_adherent("Julie", "julie@example.com");

            int e1 = b.emprunter(l1, a1, 14);
            System.out.println("Jean a emprunté le livre " + l1 + " (emprunt " + e1 + ")");
            try {
                b.emprunter(l1, a2, 14);
                System.out.println("ERREUR : l'emprunt aurait dû être refusé");
            } catch (LivreIndisponible e) {
                System.out.println("Refus attendu : " + Connexion.message(e));
            }
            for (Emprunt e : b.emprunts_en_cours(a1))
                System.out.println("En cours : #" + e.id + " " + e.titre + " (retour prévu " + e.date_retour_prevue + ")");

            b.retourner(e1);
            System.out.println("Recherche « hugo » :");
            for (Livre l : b.rechercher("hugo"))
                System.out.println("  #" + l.id + " " + l.titre + " - " + l.auteur
                        + (l.disponible ? " [disponible]" : " [emprunté]"));
            try {
                b.retourner(e1);
            } catch (Introuvable e) {
                System.out.println("Refus attendu : " + Connexion.message(e));
            }

            // Accents : le texte doit revenir identique après un aller-retour CORBA + MySQL
            String titre = "L'Étranger à Noël, œuvre";
            b.ajouter_livre(titre, "Albert Camus", "978-J2");
            boolean ok = false;
            for (Livre l : b.rechercher("Étranger")) ok |= l.titre.equals(titre);
            System.out.println("Accents : " + (ok ? "OK" : "ÉCHEC (encodage)"));
            if (!ok) System.exit(2);
            System.out.println("Test terminé avec succès.");
        } catch (Exception e) {
            System.err.println(Connexion.message(e));
            System.exit(1);
        }
        System.exit(0);
    }
}
