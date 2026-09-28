-- base.sql : création de la base, de l'utilisateur et des tables
SET NAMES utf8mb4;
-- Usage : sudo mysql < base.sql
CREATE DATABASE IF NOT EXISTS biblio CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'biblio'@'localhost' IDENTIFIED BY 'motdepasse';
GRANT ALL PRIVILEGES ON biblio.* TO 'biblio'@'localhost';
FLUSH PRIVILEGES;
USE biblio;

CREATE TABLE IF NOT EXISTS livre (
  id     INT AUTO_INCREMENT PRIMARY KEY,
  titre  VARCHAR(200) NOT NULL,
  auteur VARCHAR(150) NOT NULL,
  isbn   VARCHAR(20)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS adherent (
  id    INT AUTO_INCREMENT PRIMARY KEY,
  nom   VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- date_retour NULL = livre encore emprunté
CREATE TABLE IF NOT EXISTS emprunt (
  id                 INT AUTO_INCREMENT PRIMARY KEY,
  livre_id           INT NOT NULL,
  adherent_id        INT NOT NULL,
  date_emprunt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  date_retour_prevue DATE NOT NULL,
  date_retour        DATETIME NULL,
  FOREIGN KEY (livre_id)    REFERENCES livre(id),
  FOREIGN KEY (adherent_id) REFERENCES adherent(id)
) ENGINE=InnoDB;

-- Données de test (optionnel : supprimez ces lignes si inutile)
INSERT INTO livre(titre,auteur,isbn) VALUES
  ('Germinal','Émile Zola','978-0'),
  ('Candide','Voltaire','978-3');
INSERT INTO adherent(nom,email) VALUES
  ('Claire','claire@example.com');
