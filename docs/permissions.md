Listing User Permissions
------------------------

Lists the users that have access to a file and the their permissions on the file. The user making the request and the configured rodsadmin user are filtered out of the returned list. The user making the request must own the file.

Action: user-permissions

Error codes: ERR_NOT_A_USER, ERR_DOES_NOT_EXIST, ERR_NOT_OWNER

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/iplant/home/testuser/testfile", "/iplant/home/testuser/testfile2"]}' 'http://nibblonian.example.org/user-permissions?user=testuser'

The response body:

    {
        "action" : "user-permissions",
        "status" : "success",
        "paths" : [
            {
               "path" : "/iplant/home/testuser/testfile",
               "user-permissions" : [
                   {
                       "user" : "user1", 
                       "permissions" : {
                           "read" : true,
                           "write" : false,
                           "own" : false
                       }
                   }
               ]
            },
            {
                "path" : "/iplant/home/testuser/testfile2",
                "user-permissions" : [
                    {
                        "user" : "user2",
                        "permissions" : {
                            "read" : true,
                            "write" : false,
                            "own" : false
                        }
                    }
                ]
            }
        ]
    }
