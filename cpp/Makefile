CXX      = g++
CXXFLAGS = -std=c++11 -Wall -O2
IDL      = ../bibliotheque.idl
ORB      = $(shell pkg-config --cflags --libs omniORB4)
# mysql_config (MySQL) ou mariadb_config (MariaDB)
MYSQL_CFG = $(shell which mysql_config 2>/dev/null || which mariadb_config)
MYSQL    = $(shell $(MYSQL_CFG) --cflags --libs)

all: server

bibliotheque.hh bibliothequeSK.cc: $(IDL)
	omniidl -bcxx $(IDL)

server: server.cc bibliothequeSK.cc bibliotheque.hh
	$(CXX) $(CXXFLAGS) server.cc bibliothequeSK.cc -o $@ $(ORB) $(MYSQL) -lpthread

clean:
	rm -f server bibliotheque.hh bibliothequeSK.cc

.PHONY: all clean
