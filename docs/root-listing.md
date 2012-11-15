Top Level Root Listing
----------------------

This endpoint provides a shortcut for the front-end to list the top-level directories (i.e. the user's home directory and Community Data).

Action: "root"

Error codes: ERR_DOES_NOT_EXIST, ERR_NOT_READABLE, ERR_NOT_A_USER

Curl command:

    curl 'http://127.0.0.1::3000/root?user=wregglej'

Response:

    {
       "action":"root",
       "status":"success",
       "roots":[
          {
             "date-modified":"1340918988000",
             "hasSubDirs":true,
             "permissions":{
                "read":true,
                "write":true,
                "own":true
             },
             "date-created":"1335217160000",
             "label":"wregglej",
             "id":"\/iplant\/home\/wregglej"
          },
          {
             "date-modified":"1335476028000",
             "hasSubDirs":true,
             "permissions":{
                "read":true,
                "write":true,
                "own":false
             },
             "date-created":"1335217387000",
             "label":"Community Data",
             "id":"\/iplant\/home\/shared"
          }
       ]
    }
