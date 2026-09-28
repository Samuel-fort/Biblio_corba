#!/bin/bash
# Usage :
#   ./run.sh         lance Naming Service + serveur C++ + client Java (interface Swing)
#   ./run.sh test    lance le scénario de test en console (sans interface graphique)
# Prérequis : make -C cpp && make -C java ; base créée avec base.sql
cd "$(dirname "$0")"

# Java 8 si disponible (CORBA absent des JDK récents), sinon java du PATH
JDK="${JDK:-$(ls -d /usr/lib/jvm/java-8-openjdk-* 2>/dev/null | head -1)}"
JAVA="${JDK:+$JDK/bin/}java -Dfile.encoding=UTF-8"

NS="-ORBInitRef NameService=corbaloc::localhost:2809/NameService"
export DB_PASS="${DB_PASS:-motdepasse}"

rm -rf /tmp/omninames; mkdir -p /tmp/omninames   # Naming Service neuf à chaque lancement
omniNames -start 2809 -logdir /tmp/omninames &
NS_PID=$!
trap 'kill $SRV_PID $NS_PID 2>/dev/null' EXIT
sleep 2

./cpp/server $NS -ORBnativeCharCodeSet UTF-8 &   # UTF-8 : accents corrects avec MySQL
SRV_PID=$!
sleep 1

if [ "$1" = "test" ]; then
  $JAVA -cp java/classes ConsoleTest $NS
else
  $JAVA -cp java/classes Client $NS
fi
