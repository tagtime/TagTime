#!/usr/bin/python

#install.py 
#install script - this should work out of the box if the above path is right
############################################################################

import os,os.path,sys

if len(sys.argv) < 2 : 
  print "usage:  %s <username>  "%sys.argv[0]
  sys.exit(1)

def findcmd(cmd) :
  val = os.popen("which %s"%cmd).readline().strip()
  if val.find("no %s"%cmd) != -1 :
    return None
  return val
  
vals = {}
vals["USER"] = sys.argv[1]
vals["PATH"] = os.getcwd()+"/";
vals["HOME"] = os.path.expanduser("~")   #os.environ["HOME"]+"/"
vals["ED"]   = findcmd("vi")
vals["XT"]   = findcmd("xterm")
vals["CYGWIN"]  = ('0','1')[os.popen("uname").readline().find("CYGWIN") != -1] 


#hack for macOS
if not vals["XT"] :
  macxt = "/usr/X11R6/bin/xterm"
  if os.path.isfile(macxt) :
    vals["XT"] = macxt

globals().update(vals)

infile = open("settings.pl.template",'r')
outfile = open("settings.pl",'w')
symlink = os.path.join(HOME,".tagtimerc")
target = os.path.join(PATH,"settings.pl")


def getVar(line) :
   fields = line.split("__")
   if len(fields) == 3 :
       return fields[1]
   return None


for lineNum,line in enumerate(infile) :
  var = getVar(line)
  if var :
    if not var in vals or not vals[var] :
      print "*** WARNING ***  no value defined for __%s__"%var,"on line",lineNum,"of settings.pl.template"
      print "*** you must manually set this parameter in settings.pl for tagtime to function properly"
    else :
      pat = "__%s__"%var
      line = line.replace(pat,vals[var])
      pos = line.find(";")+1
      line = line[:pos].ljust(40)+line[pos:] 
  outfile.write(line)


if os.path.isdir(symlink) :
   print "ERROR - directory exists: ",symlink
   sys.exit(1)

if os.path.isfile(symlink) :
   os.remove(symlink)

os.system("ln -s %s %s"%(target,symlink))
print "Done! Take a look at settings.pl to see if it looks ok..."

