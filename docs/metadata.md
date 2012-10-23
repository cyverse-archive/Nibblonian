Metadata
---------------------------

The following commands allow the caller to set and get attributes on files and directories in iRODS. iRODS attributes take the form of Attribute Value Unit triples associated with directories and files. Files/directories cannot have multiple AVUs with the same attribute name, so repeated POSTings of an AVU with the same attribute name will overwrite the old value.


Setting Metadata
------------------------------------
Note the single-quotes around the request URL in the curl command.

__URL Path__: /metadata

__HTTP Method__: POST

__Action__: "set-metadata"

__Error codes__: ERR_INVALID_JSON, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Query Parameters__:
* user - iRODS username of the user making the request.
* path - The iRODS path to the file or directory that the metadata is associated with.    

__Request Body__:

    {
        "attr" : "avu_name", 
        "value" : "avu_value", 
        "unit" : "avu_unit"
    }

__Response__:

    {
        "action" : "set-metadata",
        "status" : "success",
        "path"   : "\/iplant\/home\/johnw\/LICENSE.txt",
        "user"   : "johnw"
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"attr" : "avu_name", "value" : "avu_value", "unit" : "avu_unit"}' 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'


Setting Metadata as a Batch Operation
-------------------------------------
__URL Path__: /metadata-batch

__HTTP Method__: POST

__Action__: "set-metadata-batch"

__Error codes__: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

__Request Body__:

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

__Response__:

    {
        "action" : "set-metadata-batch",
        "status" : "success",
        "path"   : "\/iplant\/home\/wregglej\/LICENSE.txt",
        "user"   :" wregglej"
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"add" : [{"attr" : "attr", "value" : "value", "unit" : "unit"}], "delete" : ["del1", "del2"]}' 'http://127.0.0.1:3000/metadata-batch?user=johnw&path=/iplant/home/johnw/LICENSE.txt'
    

Getting Metadata
------------------------------------
__URL Path__: /metadata

__HTTP Method__: GET

__Action__: "get-metadata"

__Error codes__: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

__Response__:

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

__Curl Command__:

    curl 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'


Deleting File and Directory Metadata
------------------------------------
__URL Path__: /metadata

__HTTP Method__: DELETE

__Action__: "delete-metadata"

__Error Codes__: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

__Response__:

    {
        "action":"delete-metadata",
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }

__Curl Command__:

    curl -X DELETE 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt&attr=avu_name'


