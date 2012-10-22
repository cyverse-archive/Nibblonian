Directory Rename
----------------
Action: "rename-directory"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_INCOMPLETE_RENAME, ERR_NOT_A_USER

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

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_INCOMPLETE_RENAME, ERR_NOT_A_USER

Curl command:

    curl -H "Content-Type:application/json" -d '{"source" : "/tempZone/home/rods/test3", "dest" : "/tempZone/home/rods/test2"}' http://127.0.0.1:3000/file/rename?user=rods

Response:

    {
        "action":"rename-file",
        "source":"/tempZone/home/rods/test3",
        "dest":"/tempZone/home/rods/test2",
        "status":"success"
    }

