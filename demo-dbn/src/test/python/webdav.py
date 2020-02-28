
# to run:
# python src/test/python/webdav.py
# or
# python3 src/test/python/webdav.py

# https://docs.pyfilesystem.org/en/latest/
# http://localhost:8080/qon-demo-dbn/anondav/pgsql/classicmodels.countries/ESP

import fs
import json
import logging
#import logging.handlers
import os
import requests
import sys

# https://stackoverflow.com/a/28194953
#logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)
logging.basicConfig(stream=sys.stdout, level=logging.INFO)

#####

qon_host = u"localhost:8080/qon-demo-dbn"

## no auth
#filesystem_url = u"webdav://" + qon_host + "/anondav/"

## with auth
filesystem_url = u"webdav://root:root@" + qon_host + "/webdav/"

root_folder = u"/"
#root_folder = u"qon-demo-minimal/anondav/"

#filesystem_url = u"webdav://localhost:8008/"

#folder = u"/"
row_id = u'1'


#####

def get_fs(filesystem_url, root_folder):
	filesystem = fs.open_fs(filesystem_url)
	logging.info("connect to FS: {}".format(str(filesystem)))
	#filesystem.makedirs(folder, recreate=True)
	#filesystem = filesystem.opendir(root_folder)
	return filesystem

def list_files(thisfs, folder):
	#thisfs = get_fs(filesystem_url, root_folder)
	files = thisfs.listdir(folder)
	logging.info("files: {}".format(files))

def read_file(path):
	file = open(path,"r")
	text = file.read()
	file.close()
	return text

# setup

davfs = get_fs(filesystem_url, root_folder)
localfs = fs.open_fs("/tmp")

# list files

list_files(davfs, u"/")
list_files(davfs, u"/pgsql") # FIXedME!
list_files(davfs, u"/pgsql/classicmodels.countries")

# list more files

list_files(davfs, u"/pgsql/classicmodels.countries/ESP")

# read file from webdav

fs.copy.copy_file(davfs, u"/pgsql/classicmodels.countries/ESP/name", localfs, u"ESP_name.txt")
## ll /tmp/*.txt

# write file to webdav

file = open("/tmp/testfile.txt","w")
file.truncate(0)
file.write("AA")
file.close()

fs.copy.copy_file(localfs, u"testfile.txt", davfs, u"/pgsql/classicmodels.countries/ESP/name")
fs.copy.copy_file(davfs,  u"/pgsql/classicmodels.countries/ESP/name", localfs, u"testfile2.txt")

print("content: ", read_file("/tmp/testfile2.txt"))

# delete

davfs.remove(u"/pgsql/classicmodels.countries/ESP/name")

