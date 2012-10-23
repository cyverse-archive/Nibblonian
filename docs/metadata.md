Metadata
---------------------------

The following commands allow the caller to set and get attributes on files and directories in iRODS. iRODS attributes take the form of Attribute Value Unit triples associated with directories and files. Files/directories cannot have multiple AVUs with the same attribute name, so repeated POSTings of an AVU with the same attribute name will overwrite the old value.


Setting Metadata
------------------------------------
Note the single-quotes around the request URL in the curl command.

_URL Path_: /metadata

_HTTP Method_: POST

_Action_: "set-metadata"

_Error codes_: ERR_INVALID_JSON, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

_Request Query Parameters_:
* user - iRODS username of the user making the request.
* path - The iRODS path to the file or directory that the metadata is associated with.    

_Request Body_:

    {
        "attr" : "avu_name", 
        "value" : "avu_value", 
        "unit" : "avu_unit"
    }

_Response_:

    {
        "action" : "set-metadata",
        "status" : "success",
        "path"   : "\/iplant\/home\/johnw\/LICENSE.txt",
        "user"   : "johnw"
    }

_Curl command_:

    curl -H "Content-Type:application/json" -d '{"attr" : "avu_name", "value" : "avu_value", "unit" : "avu_unit"}' 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'


Setting Metadata as a Batch Operation
-------------------------------------
URL Path: /metadata-batch

HTTP Method: POST

Action: "set-metadata-batch"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

Request Query Parameters:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

Request Body:

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

Response:

    {
        "action" : "set-metadata-batch",
        "status" : "success",
        "path"   : "\/iplant\/home\/wregglej\/LICENSE.txt",
        "user"   :" wregglej"
    }

Curl command:

    curl -H "Content-Type:application/json" -d '{"add" : [{"attr" : "attr", "value" : "value", "unit" : "unit"}], "delete" : ["del1", "del2"]}' 'http://127.0.0.1:3000/metadata-batch?user=johnw&path=/iplant/home/johnw/LICENSE.txt'
    

Getting Metadata
------------------------------------
URL Path: /metadata

HTTP Method: GET

Action: "get-metadata"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_USER

Note the single-quotes around the request URL in the curl command. Also note that the metadata returned in the "metadata" field is in a list, since this command returns all of the metadata associated with a file or directory.

Request Query Parameters:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

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

Curl command:

    curl 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt'


Deleting File and Directory Metadata
------------------------------------
URL Path: /metadata

HTTP Method: DELETE

Action: "delete-metadata"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

As before, note the single-quotes around the request URLs in the curl command. To get the directory version of the command, replace the path portion of the URL with '/directory/metadata' and make sure that the path indicated in the query portion of the URL points to a directory.

Request Query Parameters:
* user - The iRODS username of the user making the request.
* path - The path to the file or directory being operated on.

Response:

    {
        "action":"delete-metadata",
        "status":"success",
        "path":"\/iplant\/home\/johnw\/LICENSE.txt",
        "user":"johnw"
    }

Curl command:

    curl -X DELETE 'http://127.0.0.1:3000/metadata?user=johnw&path=/iplant/home/johnw/LICENSE.txt&attr=avu_name'


