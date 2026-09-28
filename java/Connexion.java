import Biblio.Bibliotheque;
import Biblio.BibliothequeHelper;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.CosNaming.NamingContextHelper;

/** Retrouve l'objet distant « Bibliotheque » via le Naming Service. */
public final class Connexion {
    private Connexion() {}

    public static Bibliotheque ouvrir(String[] args) throws Exception {
        ORB orb = ORB.init(args, null);
        org.omg.CORBA.Object ns = orb.resolve_initial_references("NameService");
        NamingContext racine = NamingContextHelper.narrow(ns);
        NameComponent[] nom = { new NameComponent("Bibliotheque", "") };
        return BibliothequeHelper.narrow(racine.resolve(nom));
    }

    /** Message lisible pour une exception CORBA / métier. */
    public static String message(Throwable t) {
        if (t instanceof Biblio.LivreIndisponible)
            return "Le livre " + ((Biblio.LivreIndisponible) t).livre_id + " est déjà emprunté.";
        if (t instanceof Biblio.LimiteAtteinte) {
            Biblio.LimiteAtteinte e = (Biblio.LimiteAtteinte) t;
            return "Limite de " + e.maximum + " emprunts atteinte pour l'adhérent " + e.adherent_id + ".";
        }
        if (t instanceof Biblio.Introuvable) {
            Biblio.Introuvable e = (Biblio.Introuvable) t;
            return "Introuvable : " + e.entite + " n° " + e.id;
        }
        if (t instanceof Biblio.ErreurBD)
            return "Erreur base de données : " + ((Biblio.ErreurBD) t).message;
        if (t instanceof org.omg.CORBA.COMM_FAILURE || t instanceof org.omg.CORBA.TRANSIENT)
            return "Serveur injoignable. Le Naming Service et le serveur sont-ils lancés ?";
        return String.valueOf(t);
    }
}
