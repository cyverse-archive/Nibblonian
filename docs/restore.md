Restoring a file or directory from a user's trash
-------------------------------------------------

Action: "restore"

Error codes: ERR_EXISTS, ERR_DOES_NOT_EXIST, ERR_NOT_A_USER, ERR_NOT_WRITEABLE

Sample Request JSON:

    {
        "paths" : ["<absolute path to trashed 
    }

Curl Command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/iplant/trash/home/proxy-user/johnworth/foo.fq", "/iplant/trash/home/proxy-user/johnworth/foo1.fq"]}' http://sample.nibblonian.org/restore?user=johnworth

Response JSON:

    {
        "action" : "restore",
        "status" : "success",
        "restored" : {
            "/iplant/trash/home/proxy-user/johnworth/foo.fq" : "/iplant/home/johnworth/foo.fq",
            "/iplant/trash/home/proxy-user/johnworth/foo1.fq" : "/iplant/home/johnworth/foo1.fq"
        }
    }

The "restored" field contains a map whose keys are the paths in the user's that were restored and whose keys are the paths the files were restored to.
