File and Directory Metadata
---------------------------

The following commands allow the caller to set attributes on files in iRODS. iRODS attributes take the form of Attribute Value Unit triples associated with directories and files. Files/directories cannot have multiple AVUs with the same attribute name, so repeated POSTings of an AVU with the same attribute name will overwrite the old value.


Setting File and Directory Metadata
------------------------------------
Note the single-quotes around the request URL in the curl command. To associate metadata with a directory, change the path portion of the request url to '/directory/metadata' (minus the quotes) and make sure that the path indicated in the query string is a directory instead of a file.

Action: "set-metadata"

Error codes: ERR_INVALID_JSON, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

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

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

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

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_USER

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

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

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
