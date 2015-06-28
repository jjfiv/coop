#!/bin/bash

set -eu

BASEURL='https://cdnjs.cloudflare.com/ajax/libs'

LIBS='jquery/2.0.3/jquery.js lodash.js/3.9.3/lodash.js react/0.13.3/JSXTransformer.js react/0.13.3/react-with-addons.js history.js/1.8/native.history.min.js'
OUT_DIR='src/main/resources/libs'

for x in ${LIBS}; do
  mkdir -p ${OUT_DIR}
  LOCAL_FILE=${OUT_DIR}/$(basename ${x})
  if [[ ! -f ${LOCAL_FILE} ]]; then
    echo wget ${BASEURL}/${x} -O ${LOCAL_FILE}
    wget ${BASEURL}/${x} -O ${LOCAL_FILE}
  fi
done

