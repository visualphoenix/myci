//
// Copyright (c) 2015, Raymond Barbiero
//
def default_bashbang() {
def bashbang = '''\
#!/bin/bash
'''
def bashbang_strict = bashbang + ' set -e -u ; '
def bashbang_debug = bashbang + ' cat \$0 ; '
def bashbang_strict_debug = bashbang_strict + ' cat \$0 ; '
  bashbang_strict
}

def build(def host_port, def tag_name, def dockerfile) {
  echo 'DOCKER BUILD'
  def docker_start = "docker -H=\"${host_port}\" build --tag ${tag_name} --file ${dockerfile} ."
  def cmd = default_bashbang() + """
WORKSPACE=\$PWD
${docker_start}
"""
  dir("${pwd()}/build") {
    sh "${cmd}"
  }
}

def start(def name, def host_port, def url, def user, def pw, def desc, def num_exec, def image, def volumes="") {
  echo 'DOCKER START'
  def docker_start = "docker -H=\"${host_port}\" pull ${image} ; docker -H=\"${host_port}\" run -d --name=\"${name}\" ${volumes} -e JENKINS_MASTER_URL=\"${url}\" -e JENKINS_SLAVE_USER=\"${user}\" -e JENKINS_SLAVE_PASSWORD=\"${pw}\" -e JENKINS_SWARM_NAME=\"${name}\" -e JENKINS_SWARM_LABELS=\"${name}\" -e JENKINS_SWARM_DESCRIPTION=\"${desc}\" -e JENKINS_SWARM_EXECUTORS=\"${num_exec}\" ${image}"
  def cmd = default_bashbang() + """
${docker_start}
"""
  sh "${cmd}"
}

def stop(def name, def host_port) {
  echo 'DOCKER STOP'
  def docker_stop = "docker -H=\"${host_port}\" stop ${name} ; docker -H=\"${host_port}\" rm -f -v ${name}"

  def cmd = default_bashbang() + """
${docker_stop}
"""
  sh "${cmd}"
}

return this;
