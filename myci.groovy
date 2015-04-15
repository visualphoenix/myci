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
  return bashbang_strict
}

def clone(def repo, def sha, def refspec) { 
  def clone_repo = """\
TMPDIR=\$(mktemp -d)
function finish {
  [ -e \$TMPDIR/git-ssh-nokeycheck.sh ] && rm -rf \$TMPDIR/git-ssh-nokeycheck.sh
  [ -e \$TMPDIR ] && rm -rf \$TMPDIR
}
trap finish EXIT

cat > \$TMPDIR/git-ssh-nokeycheck.sh <<'EOF'
#!/bin/sh
#
# Allows git to exec SSH but bypass auth warnings.
# To use, export the environment variable GIT_SSH as the location of this script,
# then run git commands as usual:
# \$ export GIT_SSH=\$TMPDIR/git-ssh-nokeycheck.sh
SSH_ORIGINAL_COMMAND="ssh \$@"
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "\$@"
EOF
chmod +x \$TMPDIR/git-ssh-nokeycheck.sh
export GIT_SSH=\$TMPDIR/git-ssh-nokeycheck.sh

git init
echo '> git --version'
git --version

echo '> git -c core.askpass=true fetch --quiet --tags --progress ${repo} +refs/heads/*:refs/remotes/origin/*'
git -c core.askpass=true fetch --tags --progress ${repo} +refs/heads/*:refs/remotes/origin/*

echo '> git config remote.origin.url ${repo}' 
git config remote.origin.url ${repo}

echo '> git config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*'
git config remote.origin.fetch +refs/heads/*:refs/remotes/origin/*

echo '> git config remote.origin.url ${repo}'
git config remote.origin.url ${repo}

echo '> git -c core.askpass=true fetch --quiet --tags --progress ${repo} +refs/heads/*:refs/remotes/origin/*'
git -c core.askpass=true fetch --quiet --tags --progress ${repo} +refs/heads/*:refs/remotes/origin/*

echo '> git rev-parse ${sha}^{commit}'
git rev-parse ${sha}^{commit}

GIT_BRANCH=\$(echo '${refspec}' | sed 's@refs/heads/@@g')
branch=\$GIT_BRANCH

for i in \$(git branch -a | grep -v HEAD | grep remotes | tr '\n' ' '); do git checkout \${i#remotes/origin/}; done

echo '> git checkout \${branch}'
git checkout \${branch}

git -c core.askpass=true submodule update --init --recursive

echo '> git reset --hard ${sha}'
git reset --hard ${sha}

find . -type d | xargs touch -t 7805200000

OS=\${OS:-`uname`}
updateFileTimeStamp()
{
  FILE_REVISION_HASH=`git rev-list HEAD "\$1" | head -n 1`
  FILE_MODIFIED_TIME=`git show --pretty=format:%ai --abbrev-commit \${FILE_REVISION_HASH} | head -n 1`
  if [ "\$OS" = 'Linux' ] ; then
    touch -d "\${FILE_MODIFIED_TIME}" \$2
  elif [ "\$OS" = 'Darwin' ] || [ "\$OS" = 'FreeBSD' ] ; then
    FORMATTED_TIMESTAMP=`date -j -f '%Y-%m-%d %H:%M:%S %z' "\${FILE_MODIFIED_TIME}" +'%Y%m%d%H%M.%S'`
    touch -t  "\${FORMATTED_TIMESTAMP}" \$2
  else
    echo "Unknown Operating System to perform timestamp update" >&2
    exit 1
  fi
}

for file in \$(git ls-files)
do
  updateFileTimeStamp "\${file}" "\${file}"
done

"""
  cmd = default_bashbang() + clone_repo
  sh "${cmd}"
}

def credential(name) {
  def v;
  withCredentials([[$class: 'UsernamePasswordBinding', credentialsId: name, variable: 'creduserpass']]) {
      v = env.creduserpass;
  }
  return v
}

def archive_repo() {
    def cmd = default_bashbang() + '''
files= ;
[ -d build ] && files="${files:-} build/" || true ;
[ -f ci.json ] && files="${files:-} ci.json" || true ;
if [ ! -z "${files}" ] ; then
  tar zcf build.tar.gz ${files} ;
fi
'''
    sh "${cmd}"
    def buildfile = new File("${pwd()}/build.tar.gz")
    if(buildfile.exists()) {
      archive 'build.tar.gz'
    } else {
      error 'Nothing to archive'
    }
}

def convertYmlToJsonFile(def inFile, outFile) {
  cmd = default_bashbang() + """
if [ -f \"${inFile}\" ] ; then
  yj < ${inFile} > ${outFile} ;
fi
"""
  sh "${cmd}"
}

def parseJsonFile(def f) {
  def conf
  def json_f = new File(f)
  if ( json_f.exists() ) {
    try {
      def json_t = readFile f
      conf = new JsonSlurper().parseText(json_t)
    } catch(e) {
      throw e
    } finally {
      json_f.delete()
    }
  } else {
    error "${f} does not exist"
  }
  return conf
}

def prepare(def ciyml, def repo, def sha, def refspec) {
  def conf
  sh default_bashbang() + "mkdir -p ${pwd()}/build"
  dir("${pwd()}/build") {
    clone(repo, sha, refspec)
  }

  def myciFile = new File("${pwd()}/build/${ciyml}")
  if ( myciFile.exists() ) {
    convertYmlToJsonFile("${pwd()}/build/${ciyml}", "${pwd()}/ci.json")
    archive_repo()
    conf = parseJsonFile("${pwd()}/ci.json")
  } else {
    error "Repository doesnt support CI"
  }
  return conf
}

def unarchive_repo() {
  unarchive mapping: [ 'build.tar.gz' : '.' ]
  def cmd = default_bashbang() + '''
tar zxf build.tar.gz
rm build.tar.gz
'''
  sh "${cmd}"
}

def try_build(def conf, def refspec) {
  def cmd = default_bashbang() + """
WORKSPACE=\$PWD
JENKINS_MASTER_URL=${JENKINS_MASTER_URL}
JENKINS_URL=${JENKINS_MASTER_URL}
GIT_BRANCH=\$(echo '${refspec}' | sed 's@refs/heads/@@g') ;
"""
  dir("${pwd()}/build") {
    def p = conf['build']
    def s = p.size()
    for (i=0; i <s; i++) {
      sh cmd + p[i]
    }
  }
}

def try_publish(def conf, def refspec) {
  def cmd = default_bashbang() + """
WORKSPACE=\$PWD
JENKINS_MASTER_URL=${JENKINS_MASTER_URL}
JENKINS_URL=${JENKINS_MASTER_URL}
GIT_BRANCH=\$(echo '${refspec}' | sed 's@refs/heads/@@g') ;
"""
  dir("${pwd()}/build") {
    def p = conf['publish']
    def s = p.size()
    for (i=0; i <s; i++) {
      sh cmd + p[i]
    }
  }
}

def try_trigger(def conf, def refspec) {
  def cmd = default_bashbang() + """
WORKSPACE=\$PWD
JENKINS_MASTER_URL=${JENKINS_MASTER_URL}
JENKINS_URL=${JENKINS_MASTER_URL}
GIT_BRANCH=\$(echo '${refspec}' | sed 's@refs/heads/@@g') ;
"""
  dir("${pwd()}/build") {
    def p = conf['trigger']
    if(p) {
      def s = p.size()
      for (i=0; i <s; i++) {
        sh cmd + p[i]
      }
    }
  }
}

def build(def conf, def refspec) {
  unarchive_repo()
  try_build(conf, refspec)
  try_publish(conf, refspec)
  try_trigger(conf, refspec)
}

return this;
