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
          "user-permissions" : [], 
          "files":[
             {
                "id":"\/tempZone\/home\/rods\/project2.clj",
                "label":"project2.clj",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/results2.txt",
                "label":"results2.txt",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads2222.txt",
                "label":"uploads2222.txt",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads333.txt",
                "label":"uploads333.txt",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploads5555.txt",
                "label":"uploads5555.txt",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadsss.txt",
                "label":"uploadsss.txt",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadtest",
                "label":"uploadtest",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/uploadtest5000",
                "label":"uploadtest5000",
                "user-permissions" : []
             }
          ],
          "folders":[
             {
                "id":"\/tempZone\/home\/rods\/bargle",
                "label":"bargle",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/big-test",
                "label":"big-test",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/fakedir2",
                "label":"fakedir2",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/foo1",
                "label":"foo1",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/foobarbaz",
                "label":"foobarbaz",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/src",
                "label":"src",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/target",
                "label":"target",
                "user-permissions" : []
             },
             {
                "id":"\/tempZone\/home\/rods\/test1",
                "label":"test1",
                "user-permissions" : []
             }
          ]
       }
    }

The user permissions fields are lists of maps in the following format:

    [{
         "user" : "username",
         "permissions" : {
             "read" : true,
             "write" : true,
             "own" : true
         }      
    }]

