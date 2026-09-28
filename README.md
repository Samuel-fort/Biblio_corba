# Gestion de bibliothèque : CORBA, serveur C++ / MySQL, client Java Swing

## Structure
    base.sql              base `biblio`, utilisateur, tables, données de test
    bibliotheque.idl      contrat CORBA partagé (C++ et Java)
    cpp/server.cc         serveur C++ (servant + MySQL)
    cpp/Makefile          omniidl + compilation du serveur
    java/Connexion.java   connexion ORB + Naming Service, messages d'erreur
    java/Client.java      interface Swing
    java/ConsoleTest.java scénario de test en console
    java/Makefile         idlj (stubs -> java/gen) + compilation
    run.sh                lance Naming Service + serveur + client

## Prérequis (Ubuntu/Debian)
    sudo apt install omniorb omniorb-nameserver libomniorb4-dev omniidl \
                     mysql-server libmysqlclient-dev build-essential pkg-config \
                     openjdk-8-jdk
CORBA a été retiré du JDK à partir de Java 11 : utilisez Java 8 pour le client
(`java -version` doit afficher 1.8). Avec un JDK récent, il faut JacORB.
Avec MariaDB : mariadb-server libmariadb-dev.

## Utilisation
    sudo mysql < base.sql
    make -C cpp
    make -C java
    ./run.sh          # interface graphique
    ./run.sh test     # scénario console

Manuellement (3 terminaux) :
    omniNames -start 2809 -logdir /tmp/omninames
    DB_PASS=motdepasse ./cpp/server -ORBnativeCharCodeSet UTF-8 \
        -ORBInitRef NameService=corbaloc::localhost:2809/NameService
    make -C java run

## Remarques
- Changez le mot de passe dans base.sql et via DB_PASS.
- Toute modification de l'IDL : refaire `make -C cpp` et `make -C java`.
- Un JDK avec `java` 1.8 doit être celui utilisé (update-alternatives --config java).
- Serveur et client sur deux machines : remplacez `localhost` par l'IP du Naming Service.
- Accents : le serveur doit être lancé avec `-ORBnativeCharCodeSet UTF-8` (déjà fait dans
  run.sh). Sans cette option, CORBA transmet en ISO-8859-1 et les données stockées dans
  MySQL (utf8mb4) sont corrompues.
