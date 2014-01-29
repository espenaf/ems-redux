#!/bin/bash
set -e

yellow() { echo -e "\033[33m$1\033[0m"; }
green() { echo -e "\033[32m$1\033[0m"; }
red() { echo -e "\033[31m$1\033[0m"; }
bold() { echo -e "\033[1;37m$1\033[0m"; }
_fancymessage() {
  echo ""
  green "\033[32m--> $1 \033[0m"
}

info() { bold "$1"; }
ask() { _fancymessage "$1"; }
fail() { red "$1"; exit 1; }

# read -i funker ikke på OSX - derav mer kronglete løsning :(
_readWithDefault() {
    local default=$1
    read answer
    if [ "$answer" = "" ]; then
         answer="$default"
    fi
    echo $answer
}

info "BYGGER"
./sbt ems-dist/dist

DEFAULT_ZIP=`find . -name ems*.zip`
ask "Hvor ligger zip-filen? [$DEFAULT_ZIP]"
ZIP=$(_readWithDefault $DEFAULT_ZIP)

ask "Til test eller prod? [test]"
ENV=$(_readWithDefault "test")

ask "Er det første gang du deployer til dette miljøet? [y|N]"
FIRST_TIME=$(_readWithDefault "n")

if [ ! -f $ZIP ]; then
    fail "Fant ikke $ZIP :("
fi

if [ $ENV != "test" -a $ENV != "prod" ]; then
    fail "Miljø må være enten 'test' eller 'prod'"
fi

if [ $ENV == "prod" ]; then
    HOST="212.71.237.26"
    EMS_BASE="/home/javabin/web/ems"
elif [ $ENV == "test" ]; then
    HOST="212.71.238.251"
    EMS_BASE="/home/javabin/web/ems"
else
    fail "Det du sa gav null mening!"
fi

info "Deployer zip $ZIP til $ENV på $HOST:$BASE/ems.zip"
scp $ZIP javabin@$HOST:$EMS_BASE/ems.zip

if [ $FIRST_TIME == 'y' -o $FIRST_TIME == 'Y' ]; then
    info "Initierer app.sh for EMS"
    ssh javabin@$HOST "cd $EMS_BASE && \
                       app init -d ems file $EMS_BASE/ems.zip"
    info "Starter EMS"
    ssh javabin@$HOST "cd $EMS_BASE/ems && app start"
else
    info "Oppdaterer og restarter EMS"
    ssh javabin@$HOST "cd $EMS_BASE/ems && \
                       app upgrade && app restart"
fi