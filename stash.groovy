//
// Copyright (c) 2015, Raymond Barbiero
//
import groovy.json.*

def default_bashbang() {
def bashbang = '''\
#!/bin/bash
'''
def bashbang_strict = bashbang + ' set -e -u ; '
def bashbang_debug = bashbang + ' cat \$0 ; '
def bashbang_strict_debug = bashbang_strict + ' cat \$0 ; '
  bashbang_strict
}

def notifyJson(def json, user, password) {
  def curl_silent = ' -sS '
  def curl_retry = ' --max-time 10 --retry 3 --retry-delay 5 --retry-max-time 32 '
  def curl_user = ' -u' + " \"${user}:${password}\" "
  def curl_content = ' -H "Content-Type: application/json" '
  def curl_post = ' -X POST '
  def curl_stash_post = ' curl ' + curl_silent + curl_retry + curl_user + curl_content + curl_post
  def notify_url = " \"${STASH_URL}/rest/build-status/1.0/commits/${sha}\" "
  def envopts = "JSON=\'${json}\' ; "
  def cmd = default_bashbang() + envopts
  cmd = cmd + curl_stash_post + notify_url + ' -d @<(echo "\$JSON") '
  sh cmd
}

def generateStatusJson(def status) {
  def stash_notify_template = [ 
         state: "${status}", 
         key: project_key, 
         name: "${env.JOB_NAME} #${env.BUILD_NUMBER}", 
         url: "${STASH_URL}/projects/${project_key}/repos/${repository_slug}",
         description: "built by Jenkins @ ${JENKINS_MASTER_URL}"
  ]
  JsonOutput.toJson(stash_notify_template)
}

def notify(def status, user, password) {
  echo 'NOTIFY STASH: ' + status
  notifyJson(generateStatusJson(status), user, password)
}

return this;
