
# to run:
# python src/test/python/webdav.py
# or
# python3 src/test/python/webdav.py

# https://docs.pyfilesystem.org/en/latest/
# http://localhost:8080/qon-demo-minimal/anondav/PUBLIC.SAMPLE_TABLE/230/DESCRIPTION

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

qon_host = u"localhost:8080/qon-demo-minimal"
#filesystem_url = "webdav://localhost:8080/qon-demo-minimal/webdav/"
#filesystem_url = u"webdav://localhost:8080/qon-demo-minimal/anondav/"
filesystem_url = u"webdav://" + qon_host + "/anondav/"
#filesystem_url = u"webdav://localhost:8080/"

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
list_files(davfs, u"/PUBLIC.SAMPLE_TABLE")
##list_files(davfs, u"PUBLIC.SAMPLE_TABLE")
##list_files(davfs, u"/PUBLIC.SAMPLE_TABLE/230")
##list_files(davfs, u"PUBLIC.SAMPLE_TABLE/230")
##list_files(davfs, u"PUBLIC.SAMPLE_TABLE/230/ID") # Error: should be a directory

# insert row

# https://stackoverflow.com/a/26045274
payload = {'ID': '1', 'NAME': 'some name', 'DESCRIPTION': 'bla bla'}
req_url = "http://" + qon_host + "/q/PUBLIC.SAMPLE_TABLE"
headers = {"content-type": "application/json" }
r = requests.post(req_url, data=json.dumps(payload), headers=headers)
logging.info("request:: %s :: %s", req_url, r)

# list more files

list_files(davfs, u"/PUBLIC.SAMPLE_TABLE/1")
##list_files(davfs, u"PUBLIC.SAMPLE_TABLE/1/ID") # Error: should be a directory

# read file from webdav

fs.copy.copy_file(davfs, u"PUBLIC.SAMPLE_TABLE/1/ID", localfs, u"ID.txt")
fs.copy.copy_file(davfs, u"PUBLIC.SAMPLE_TABLE/1/DESCRIPTION", localfs, u"DESCRIPTION.txt")
## ll /tmp/*.txt

# write file to webdav

file = open("/tmp/testfile.txt","w")
file.truncate(0)
file.write("AA")
file.close()

fs.copy.copy_file(localfs, u"testfile.txt", davfs, u"PUBLIC.SAMPLE_TABLE/1/DESCRIPTION")
fs.copy.copy_file(davfs, u"PUBLIC.SAMPLE_TABLE/1/DESCRIPTION", localfs, u"testfile2.txt")

#file = open("/tmp/testfile2.txt","r")
#text = file.read()
#file.close()
#print("content: ", text)
print("content: ", read_file("/tmp/testfile2.txt"))

# delete

#davfs.remove(u"PUBLIC.SAMPLE_TABLE/230/DESCRIPTION")

