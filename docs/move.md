Directory Move
--------------
Action: "move-dirs"

Error codes: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_NOT_A_USER

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

Error codes: ERR_NOT_A_FILE, ERR_DOES_NOT_EXIST, ERR_NOT_A_FOLDER, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_NOT_A_USER

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

