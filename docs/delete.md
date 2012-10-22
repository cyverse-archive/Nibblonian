Directory Deletion
------------------
Action: "delete-dirs"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

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

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_NOT_A_USER

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/rods/test2"]}' http://127.0.0.1:3000/file/delete?user=rods

Response:

    {
        "action":"delete-files",
        "paths":["/tempZone/home/rods/test2"]
        "status" : "success"
    }

