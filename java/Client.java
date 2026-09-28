import Biblio.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Interface Swing : les appels CORBA se font hors du thread graphique. */
public class Client extends JFrame {
    private final Bibliotheque biblio;
    private final JLabel statut = new JLabel(" ");

    // Catalogue
    private final DefaultTableModel modeleLivres = modele("N°", "Titre", "Auteur", "ISBN", "Disponible");
    private final JTextField recherche = new JTextField(20);

    // Emprunts
    private final DefaultTableModel modeleEmprunts = modele("N° emprunt", "N° livre", "Titre", "Emprunté le", "Retour prévu");
    private final JTable tableEmprunts = new JTable(modeleEmprunts);
    private final JTextField champAdherent = new JTextField(6);
    private final JTextField champLivre = new JTextField(6);
    private final JTextField champJours = new JTextField("14", 4);

    public Client(Bibliotheque biblio) {
        super("Gestion de bibliothèque (CORBA)");
        this.biblio = biblio;
        JTabbedPane onglets = new JTabbedPane();
        onglets.addTab("Catalogue", panneauCatalogue());
        onglets.addTab("Adhérents", panneauAdherents());
        onglets.addTab("Emprunts", panneauEmprunts());
        add(onglets, BorderLayout.CENTER);
        statut.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statut, BorderLayout.SOUTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(820, 480);
        setLocationRelativeTo(null);
        actualiserCatalogue(null);
    }

    // ---------- panneaux ----------
    private JPanel panneauCatalogue() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel haut = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton bRech = new JButton("Rechercher");
        JButton bTout = new JButton("Tout afficher");
        haut.add(new JLabel("Titre ou auteur :"));
        haut.add(recherche); haut.add(bRech); haut.add(bTout);
        bRech.addActionListener(e -> actualiserCatalogue(recherche.getText().trim()));
        recherche.addActionListener(e -> actualiserCatalogue(recherche.getText().trim()));
        bTout.addActionListener(e -> { recherche.setText(""); actualiserCatalogue(null); });

        JTextField titre = new JTextField(14), auteur = new JTextField(12), isbn = new JTextField(9);
        JButton bAjout = new JButton("Ajouter le livre");
        JPanel bas = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bas.add(new JLabel("Titre")); bas.add(titre);
        bas.add(new JLabel("Auteur")); bas.add(auteur);
        bas.add(new JLabel("ISBN")); bas.add(isbn); bas.add(bAjout);
        bAjout.addActionListener(e -> {
            if (titre.getText().trim().isEmpty() || auteur.getText().trim().isEmpty()) {
                info("Le titre et l'auteur sont obligatoires."); return;
            }
            appeler(() -> biblio.ajouter_livre(titre.getText().trim(), auteur.getText().trim(), isbn.getText().trim()),
                id -> {
                    statut("Livre ajouté (n° " + id + ").");
                    titre.setText(""); auteur.setText(""); isbn.setText("");
                    actualiserCatalogue(null);
                });
        });

        p.add(haut, BorderLayout.NORTH);
        p.add(new JScrollPane(new JTable(modeleLivres)), BorderLayout.CENTER);
        p.add(bas, BorderLayout.SOUTH);
        return p;
    }

    private JPanel panneauAdherents() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JTextField nom = new JTextField(14), email = new JTextField(18);
        JButton b = new JButton("Ajouter l'adhérent");
        p.add(new JLabel("Nom")); p.add(nom);
        p.add(new JLabel("Email")); p.add(email); p.add(b);
        b.addActionListener(e -> {
            if (nom.getText().trim().isEmpty() || email.getText().trim().isEmpty()) {
                info("Le nom et l'email sont obligatoires."); return;
            }
            appeler(() -> biblio.ajouter_adherent(nom.getText().trim(), email.getText().trim()),
                id -> {
                    info("Adhérent créé. Son numéro est le " + id + " (à utiliser pour les emprunts).");
                    statut("Adhérent n° " + id + " créé.");
                    nom.setText(""); email.setText("");
                });
        });
        return p;
    }

    private JPanel panneauEmprunts() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel haut = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton bEmp = new JButton("Emprunter");
        JButton bListe = new JButton("Voir ses emprunts");
        haut.add(new JLabel("N° adhérent")); haut.add(champAdherent);
        haut.add(new JLabel("N° livre")); haut.add(champLivre);
        haut.add(new JLabel("Jours")); haut.add(champJours);
        haut.add(bEmp); haut.add(bListe);
        bEmp.addActionListener(e -> emprunter());
        bListe.addActionListener(e -> actualiserEmprunts());

        JButton bRet = new JButton("Retourner le livre sélectionné");
        bRet.addActionListener(e -> retourner());
        JPanel bas = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bas.add(bRet);

        p.add(haut, BorderLayout.NORTH);
        p.add(new JScrollPane(tableEmprunts), BorderLayout.CENTER);
        p.add(bas, BorderLayout.SOUTH);
        return p;
    }

    // ---------- actions ----------
    private void actualiserCatalogue(String motif) {
        Callable<Livre[]> tache = (motif == null || motif.isEmpty())
                ? () -> biblio.lister_livres()
                : () -> biblio.rechercher(motif);
        appeler(tache, livres -> {
            modeleLivres.setRowCount(0);
            for (Livre l : livres)
                modeleLivres.addRow(new Object[]{l.id, l.titre, l.auteur, l.isbn, l.disponible ? "oui" : "non"});
            statut(livres.length + " livre(s).");
        });
    }

    private void emprunter() {
        Integer a = entier(champAdherent, "N° adhérent"), l = entier(champLivre, "N° livre"),
                j = entier(champJours, "Jours");
        if (a == null || l == null || j == null) return;
        appeler(() -> biblio.emprunter(l, a, j), id -> {
            statut("Emprunt n° " + id + " enregistré.");
            actualiserCatalogue(null);
            actualiserEmprunts();
        });
    }

    private void actualiserEmprunts() {
        Integer a = entier(champAdherent, "N° adhérent");
        if (a == null) return;
        appeler(() -> biblio.emprunts_en_cours(a), liste -> {
            modeleEmprunts.setRowCount(0);
            for (Emprunt e : liste)
                modeleEmprunts.addRow(new Object[]{e.id, e.livre_id, e.titre, e.date_emprunt, e.date_retour_prevue});
            statut(liste.length + " emprunt(s) en cours.");
        });
    }

    private void retourner() {
        int ligne = tableEmprunts.getSelectedRow();
        if (ligne < 0) { info("Sélectionnez d'abord un emprunt dans la liste."); return; }
        int idEmprunt = (Integer) modeleEmprunts.getValueAt(ligne, 0);
        appeler(() -> { biblio.retourner(idEmprunt); return idEmprunt; }, id -> {
            statut("Emprunt n° " + id + " clôturé.");
            actualiserCatalogue(null);
            actualiserEmprunts();
        });
    }

    // ---------- utilitaires ----------
    private <T> void appeler(Callable<T> tache, Consumer<T> succes) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return tache.call(); }
            @Override protected void done() {
                try {
                    succes.accept(get());
                } catch (ExecutionException e) {
                    String m = Connexion.message(e.getCause());
                    statut(m);
                    JOptionPane.showMessageDialog(Client.this, m, "Erreur", JOptionPane.WARNING_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private static DefaultTableModel modele(String... colonnes) {
        return new DefaultTableModel(colonnes, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private Integer entier(JTextField champ, String nom) {
        try {
            return Integer.valueOf(champ.getText().trim());
        } catch (NumberFormatException e) {
            info(nom + " doit être un nombre entier.");
            return null;
        }
    }

    private void statut(String s) { statut.setText(s); }
    private void info(String s) { JOptionPane.showMessageDialog(this, s); }

    public static void main(String[] args) {
        Bibliotheque b;
        try {
            b = Connexion.ouvrir(args);
        } catch (Exception e) {
            System.err.println(Connexion.message(e));
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> new Client(b).setVisible(true));
    }
}
