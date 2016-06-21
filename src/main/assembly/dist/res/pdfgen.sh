#!/usr/bin/env bash

#############################################################################################
## Comment out the command that you do NOT want to use and fill in the variables if needed ##
#############################################################################################

# server run:
weasyprint -e utf8 -f pdf - -

# local run:
#USER_HOST="..." # to be filled in by the developer; this is the 'user@host' for Vagrant
#PRIVATE_KEY="..." # to be filled in by the developer; this is the private key for ssh-ing into Vagrant
#ssh -oStrictHostKeyChecking=no -i ${PRIVATE_KEY} ${USER_HOST} weasyprint -e utf8 -f pdf - -
