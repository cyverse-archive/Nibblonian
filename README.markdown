Nibblonian
==========

This is a REST-like HTTP API for iRODS written on top of jargon-core. 

All commands respond to POST requests, unless otherwise noted. A successful call will have a 200 status code, while an error will have either a 400, 404, or 500 status code. You must set the "Content-Type" header to "application/json" for any POST requests that upload JSON. Failing to do so will result in an error message.

All commands take a "user" query string parameter. This is the iRODS user that is requesting the operation. The service runs as an iRODS superuser and performs actions on the behalf of the user specified in the query string.

Also worth noting is that I've pretty-printed the JSON from the response bodies, but they aren't actually returned like that when you make a call.


Configuring Nibblonian
----------------------

Nibblonian pulls its configuration from Zookeeper. The properties must be loaded into Zookeeper with Clavin.

There is also a log4j config file located at /etc/nibblonian/log4j.properties. It's a normal log4j properties file that looks like this by default.

    # Jargon-related configuration
    log4j.rootLogger=WARN, A, B

    log4j.appender.B=org.apache.log4j.ConsoleAppender
    log4j.appender.B.layout=org.apache.log4j.PatternLayout
    log4j.appender.B.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

    log4j.appender.A=org.apache.log4j.FileAppender
    log4j.appender.A.File=nibblonian.log
    log4j.appender.A.layout=org.apache.log4j.PatternLayout
    log4j.appender.A.layout.ConversionPattern=%d{MM-dd@HH:mm:ss} %-5p (%13F:%L) %3x - %m%n

The iRODS configuration should look fairly similar to other systems that interact with iRODS. defaultResource is mentioned on the off-chance that we need it, but it's fine to leave it blank.

The log4j configuration section is just a bog-standard log4j configuration. It configures two loggers by default, one that goes to stdout and another that goes to a log file. You might want to disable the ConsoleAppender, but leaving it in shouldn't hurt anything.

Error Codes
-----------

When it encounters an error, Nibblonian will generally return a JSON object in the form:

    {
        "action" : "general name of the service",
        "status" : "failure",
        "error_code" : "ERR_CODE"
    }
    
Other entries may be included in the map, but you shouldn't depend on them being there for error checking.

A list of the known error codes is maintained here: [error codes](https://github.com/iPlantCollaborativeOpenSource/Nibblonian/blob/master/src/nibblonian/error_codes.clj)

In other cases Nibblonian should return a stacktrace. Work is planned to convert Nibblonian's error handling over to [slingshot](https://github.com/scgilardi/slingshot) (I didn't know about slingshot when I originally wrote Nibblonian).

File/Directory Sharing
----------------------
Shares a file or directory with another user. The user being shared with is given read-only access to all of the parent directories as well. This allows the user to drill down to the shared file/directory from the "Shared" section of the data management window.

Action: "share"

Error codes: ERR_NOT_A_USER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Request body JSON:

    {
        "path" : "/path/to/shared/file",
        "user" : "shared-with-user",
        "permissions" : {
            "read" : true,
            "write" : true,
            "own" : false
        }
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"path" : "/path/to/shared/file", "user" : "shared-with-user", "permissions" : {"read" : true, "write" : true, "own" : false}}' http://nibblonian.yourhostname.org/share?user=fileowner

The response body:

    {
        "action" : "share",
        "status" : "success",
        "user" : "user shared with",
        "path" : "the path that was shared",
        "permissions" : "the new permissions on the path"
    }

File/Directory Unsharing
------------------------
Unshares a file or directory. All ACLs for the specified user are removed from the file or directory. To simply change existing ACLs, recall the /share end-point with the desired permissions.

Action: "unshare"

Error codes: ERR_NOT_A_USER, ERR_BAD_OR_MISSING_FIELD, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Request body JSON

    {
        "path" : "/path/to/shared/file",
        "user" : "shared-with-user"
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"path" : "/path/to/shared/file", "user" : "shared-with-user"}' http://nibblonian.yourhostname.org/unshare?user=fileowner


File Upload
-----------
Uploads are now handled by iDrop Lite. Nibblonian is only responsible for generating a temporary password for a user and returning connection information.

Action: "upload"

Error codes: ERR_NOT_A_USER

Curl command:

    curl http://nibblonian.example.org/upload?user=muahaha

The response body:

    {
        "action":"upload",
        "status":"success",
        "data": {
                    "user":"muahaha",
                    "password":"c5dbff21fa123d5c726f27cff8279d70",
                    "host":"blowhole.example.org",
                    "port":1247,
                    "zone":"tempZone",
                    "defaultStorageResource":"",
                    "key":"1325877857614"
                }
    }


File Download
-------------
Downloads are now handled by iDrop Lite. Nibblonian handles serializing the shopping cart object and returning a temporary password.

Action: "download"

Error codes: ERR_NOT_A_USER

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/muahaha/test.txt"]}' 'http://nibblonian.example.org/download?user=muahaha'

The response:

    {
        "action":"download",
        "status":"success",
        "data": {
                    "user":"muahaha",
                    "password":"cc181a5a97635c7b45a3b2b828f964fe",
                    "host":"blowhole.example.org",
                    "port":1247,
                    "zone":"tempZone",
                    "defaultStorageResource":"",
                    "key":"1325878326128"
                }
    }

Directory Creation
------------------
Action: "create"

Error codes: ERR_NOT_WRITEABLE, ERR_EXISTS

Curl command:

    curl -H "Content-Type:application/json" -d '{"path" : "/tempZone/home/rods/test3"}' http://127.0.0.1:3000/directory/create

The response body:

    {
       "action":"create",
       "path":"\/tempZone\/home\/rods\/test3",
       "status":"success"
    }


Directory List (Non-Recursive)
------------------------------
Action: "list-dir"

Error codes: ERR_NOT_A_USER, ERR_NOT_READABLE

Curl command:

    curl http://127.0.0.1:3000/directory?user=rods&includefiles=1

If the "includefiles" is 0 or left out of the params, then the files section will be empty.
If you want to list a directory other than rods' home directory, then include a URL encoded
path parameter, like so:

    curl http://127.0.0.1:3000/directory?user=rods&includefiles=1&path=/iplant/home/rods/ugh

Response for the first curl command:

    {
       "action":"list",
       "path":{
          "id":"\/tempZone\/home\/rods",
          "label":"rods",
          "files":[
             {
                "id":"\/tempZone\/home\/rods\/project2.clj",
                "label":"project2.clj"
             },
             {
                "id":"\/tempZone\/home\/rods\/results2.txt",
                "label":"results2.txt"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads2222.txt",
                "label":"uploads2222.txt"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads333.txt",
                "label":"uploads333.txt"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads5555.txt",
                "label":"uploads5555.txt"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadsss.txt",
                "label":"uploadsss.txt"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadtest",
                "label":"uploadtest"
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadtest5000",
                "label":"uploadtest5000"
             }
          ],
          "folders":[
             {
                "id":"\/tempZone\/home\/rods\/bargle",
                "label":"bargle"
             },
             {
                "id":"\/tempZone\/home\/rods\/big-test",
                "label":"big-test"
             },
             {
                "id":"\/tempZone\/home\/rods\/fakedir2",
                "label":"fakedir2"
             },
             {
                "id":"\/tempZone\/home\/rods\/foo1",
                "label":"foo1"
             },
             {
                "id":"\/tempZone\/home\/rods\/foobarbaz",
                "label":"foobarbaz"
             },
             {
                "id":"\/tempZone\/home\/rods\/src",
                "label":"src"
             },
             {
                "id":"\/tempZone\/home\/rods\/target",
                "label":"target"
             },
             {
                "id":"\/tempZone\/home\/rods\/test1",
                "label":"test1"
             }
          ]
       }
    }


Directory Move
--------------
Action: "move-dirs"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS

The ERR_DOES_NOT_EXIST error code pops up when the destination directory does not exist and when one of the "sources" directories does not exist. The ERR_EXISTS code pops up when one of the new destination directories already exists.

Curl command:

    curl -H "Content-Type:application/json" -d '{"sources" : ["/tempZone/home/rods/test1"], "dest" : "/tempZone/home/rods/test"}' http://127.0.0.1:3000/directory/move?user=rods

Response:

    {
        "action":"move-dirs",
        "dest":"/tempZone/home/rods/test",
        "paths":[
            "/tempZone/home/rods/test1"
        ],
        "status" : "success"
    }


File Move
---------
Action: "move-files"

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_A_FOLDER, ERR_NOT_WRITEABLE, ERR_EXISTS

The ERR_DOES_NOT_EXIST error code pops up when the destination directory does not exist and when one of the "sources" files does not exist. The ERR_EXISTS code pops up when one of the new destination files already exists.

Curl command:

    curl -H "Content-Type:application/json" -d '{"sources" : ["/tempZone/home/rods/test1"], "dest" : "/tempZone/home/rods/test"}' http://127.0.0.1:3000/file/move?user=rods

Response:

    {
        "action":"move-files",
        "dest":"/tempZone/home/rods/test",
        "paths":[
            "/tempZone/home/rods/test1"
        ],
        "status" : "success"
    }


Directory Rename
----------------
Action: "rename-directory"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_INCOMPLETE_RENAME

Curl command:

    curl -H "Content-Type:application/json" -d '{"source" : "/tempZone/home/rods/test3", "dest" : "/tempZone/home/rods/test2"}' http://127.0.0.1:3000/directory/rename?user=rods

Response:

    {
        "action":"rename-directory",
        "source":"/tempZone/home/rods/test3",
        "dest":"/tempZone/home/rods/test2",
        "status":"success"
    }


File Rename
-----------
Action: "rename-file"

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_INCOMPLETE_RENAME

Curl command:

    curl -H "Content-Type:application/json" -d '{"source" : "/tempZone/home/rods/test3", "dest" : "/tempZone/home/rods/test2"}' http://127.0.0.1:3000/file/rename?user=rods

Response:

    {
        "action":"rename-file",
        "source":"/tempZone/home/rods/test3",
        "dest":"/tempZone/home/rods/test2",
        "status":"success"
    }


Directory Deletion
------------------
Action: "delete-dirs"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/rods/test2"]}' http://127.0.0.1:3000/directory/delete?user=rods

Response:

    {
        "action":"delete-dirs",
        "paths":["/tempZone/home/rods/test2"]
        "status" : "success"
    }


File Deletion
-------------
Action: "delete-files"

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/rods/test2"]}' http://127.0.0.1:3000/file/delete?user=rods

Response:

    {
        "action":"delete-files",
        "paths":["/tempZone/home/rods/test2"]
        "status" : "success"
    }


File Preview
------------
Action: "preview"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_FILE


Curl command:

    curl http://127.0.0.1:3000/file/preview?user=johnw&path=/iplant/home/johnw/LICENSE.txt

Response:

    {
        "action" : "preview",
        "preview" : "Copyright (c) 2011, The Arizona Board of Regents on behalf of \nThe University of Arizona\n\nAll rights reserved.\n\nDeveloped by: iPlant Collaborative as a collaboration between\nparticipants at BIO5 at The University of Arizona (the primary hosting\ninstitution), Cold Spring Harbor Laboratory, The University of Texas at\nAustin, and individual contributors. Find out more at \nhttp:\/\/www.iplantcollaborative.org\/.\n\nRedistribution and use in source and binary forms, with or without \nmodification, are permitted provided that the following conditions are\nmet:\n\n * Redistributions of source code must retain the above copyright \n   notice, this list of conditions and the following disclaimer.\n * Redistributions in binary form must reproduce the above copyright \n   notice, this list of conditions and the following disclaimer in the \n   documentation and\/or other materials provided with the distribution.\n * Neither the name of the iPlant Collaborative, BIO5, The University \n   of Arizona, Cold Spring Harbor Laboratory, The University of Texas at \n   Austin, nor the names of other contributors may be used to endorse or \n   promote products derived from this software without specific prior \n   written permission.\n\nTHIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS\nIS\" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED \nTO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A \nPARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT \nHOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,\nSPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED\nTO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR\nPROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF\nLIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING\nNEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS \nSOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.\n"
    }


File manifest
-------------
Action: "manifest"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_A_FILE, ERR_NOT_READABLE

Curl command:

    curl http://127.0.0.1:3000/file/manifest?user=johnw&path=/iplant/home/johnw/LICENSE.txt

Response:

    {
        "action":"manifest",
        "rawcontents":"file\/download?user=johnw&path=\/iplant\/home\/johnw\/LICENSE.txt"
    }

OR

    {
        "action":"manifest",
        "preview":"file\/preview?user=johnw&path=\/iplant\/home\/johnw\/LICENSE.txt"
    }

If the raw content is too large a preview will be sent instead. Additionally, if the file's name ends with .png, then the field containing the URL path to the download will be called "png". If the file's name ends with ".pdf", then the field will be named "pdf". Additionally, if the file is a PDF, then the query parameter "attachment=0" is added to the download path, which allows browsers to use their default PDF viewer, if they have one.


File/Directory existence
------------------------
The /exists endpoint allows the caller to check for the existence of a set of files. The following is an example call to the exists endpoint:

Action(s): N/A

Error codes: N/A

Curl command:

    curl -H "Content-type:application/json" -d '{"paths" : ["/iplant/home/wregglej/pom.xml", "/iplant/home/wregglej/pom.xml2"]}' 'http://127.0.0.1:3000/exists?user=wregglej'

Response:

    {
        "action":"exists",
        "status":"success",
        "paths":{
            "/iplant/home/wregglej/pom.xml2":false,
            "/iplant/home/wregglej/pom.xml":false
        }
    }


File and Directory Metadata
---------------------------

The following commands allow the caller to set attributes on files in iRODS. iRODS attributes take the form of Attribute Value Unit triples associated with directories and files. Files/directories cannot have multiple AVUs with the same attribute name, so repeated POSTings of an AVU with the same attribute name will overwrite the old value.


Setting File and Directory Metadata
------------------------------------
Note the single-quotes around the request URL in the curl command. To associate metadata with a directory, change the path portion of the request url to '/directory/metadata' (minus the quotes) and make sure that the path indicated in the query string is a directory instead of a file.

Action: "set-metadata"

Error codes: ERR_INVALID_JSON, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

Curl command:

    curl -H "Content-Type:application/json" -d '{"attr" : "avu_name", "value" : "avu_value", "unit" : "avu_unit"}' 'http://127.0.0.1:3000/file/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'

Response:

    {
        "action" : "set-metadata",
        "status" : "success",
        "path"   : "\/iplant\/home\/johnw\/LICENSE.txt",
        "user"   : "johnw"
    }
    

Setting File and Directory Metadata Batch
-----------------------------------------
Action: "set-metadata-batch"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

The endpoints for this command are /directory/metadata-batch and /file/metadata-batch. It accepts a POST request containing JSON in the following format:

    { 
        "add": [ 
            { 
                "attr": "attr", 
                "value": "value", 
                "unit": "unit" 
            }, 
            { 
                "attr": "attr1", 
                "value": "value", 
                "unit": "unit" 
            } 
        ], 
        "delete": [ 
            "del1", 
            "del2" 
        ] 
    } 
    
Both "add" and "delete" lists must be present even if they are empty.

Curl command:

    curl -H "Content-Type:application/json" -d '{"add" : [{"attr" : "attr", "value" : "value", "unit" : "unit"}], "delete" : ["del1", "del2"]}' 'http://127.0.0.1:3000/file/metadata-batch?user=johnw&path=/iplant/home/johnw/LICENSE.txt'
    
Response:

    {
        "action" : "set-metadata-batch",
        "status" : "success",
        "path"   : "\/iplant\/home\/wregglej\/LICENSE.txt",
        "user"   :" wregglej"
    }
    

Getting File and Directory Metadata
------------------------------------
Action: "get-metadata"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE

Note the single-quotes around the request URL in the curl command. Also note that the metadata returned in the "metadata" field is in a list, since this command returns all of the metadata associated with a file or directory.

To get metadata associated with directories, change the path portion of the URL to '/directory/metadata' and make sure that the path listed in the query portion of the URL is actually a directory.

Curl command:

    curl 'http://127.0.0.1:3000/file/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'

Response:

    {
        "action": "get-metadata",
        "status": "success",
        "metadata": [
            {
                 "attr": "avu_name",
                 "value": "avu_value",
                 "unit": "avu_unit"
            }
        ]
    }


Deleting File and Directory Metadata
------------------------------------
Action: "delete-metadata"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

As before, note the single-quotes around the request URLs in the curl command. To get the directory version of the command, replace the path portion of the URL with '/directory/metadata' and make sure that the path indicated in the query portion of the URL points to a directory.

Curl command:

    curl -X DELETE 'http://127.0.0.1:3000/file/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt&attr=avu_name'

Response:

    {
        "action":"delete-metadata",
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }


Setting a Tree URL for a File
-----------------------------
Multiple tree URLs can be associated with a file. To support this, multiple POSTs of tree-urls for the same file will append the URLs to the list stored in iRODS. Multiple tree URLs can be associated with a file in a single request. You still need to place a URL in a list even if you're only associated one URL with a file.

Something to note is that we're not checking to make sure that the strings in the 'tree-urls' list are actually URLs. This is intentional and will hopefully make migrating to a token based tree retrieval a little simpler.

Action: "set-tree-urls"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

Curl command:

    curl -d '{"tree-urls" : [{"label" : "google", "url" : "http://www.google.com"}, {"label" : "yahoo", "url" : "http://www.yahoo.com"}]}' 'http://127.0.0.1:3000/file/tree-urls?user=johnw&path=/iplant/home/johnw/LICENSE.txt'

Response:

    {
        "action":"set-tree-urls",
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }


Getting a Tree URL for a File
-----------------------------

Action: "get-tree-urls"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE

Curl command:

    curl 'http://127.0.0.1:3000/file/tree-urls?user=rods&path=/iplant/home/johnw/LICENSE.txt'

Response:

    [
        {
            "label" : "google",
            "url" : "http://www.google.com"
        },
        {
            "label" : "yahoo",
            "url" : "http://www.yahoo.com"
        }
    ]


Deleting a Tree URL
-------------------

See the instructions for deleting metadata associated with a file. The attribute name for the tree URLs is 'tree-urls'.


