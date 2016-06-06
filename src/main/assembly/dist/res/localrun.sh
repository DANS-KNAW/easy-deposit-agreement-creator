#!/usr/bin/env bash

USER_HOST="..." # to be filled in by the developer; this is the 'user@host' for Vagrant
PRIVATE_KEY="..." # to be filled in by the developer; this is the private key for ssh-ing into Vagrant

ssh -oStrictHostKeyChecking=no -i ${PRIVATE_KEY} ${USER_HOST} weasyprint -e utf8 -f pdf - -
