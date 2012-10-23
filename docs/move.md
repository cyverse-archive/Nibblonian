Moving Files and/or Directories
--------------
__URL Path__: /move

__HTTP Method__: POST

__Action__: "move"

__Error codes__: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_NOT_A_USER

The ERR_DOES_NOT_EXIST error code pops up when the destination directory does not exist and when one of the "sources" directories does not exist. The ERR_EXISTS code pops up when one of the new destination directories already exists.

__Request Query Parameters__:
* url - The iRODS username of the user making the request.

__Request Body__:

    {
        "sources" : [
            "/tempZone/home/rods/test1"
        ], 
        "dest" : "/tempZone/home/rods/test"
    }

"sources" can contain a mix of files and directories.

__Response__:

    {
        "action":"move-dirs",
        "dest":"/tempZone/home/rods/test",
        "paths":[
            "/tempZone/home/rods/test1"
        ],
        "status" : "success"
    }


__Curl Command__:

    curl -H "Content-Type:application/json" -d '' http://127.0.0.1:3000/move?user=rods


