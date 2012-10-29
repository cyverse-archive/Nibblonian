Renaming a File or Directory
----------------------------
__URL Path__: /rename

__HTTP Method__: POST 

__Action__: "rename"

__Error codes__: ERR_NOT_A_FOLDER, ERR_DOES_NOT_EXIST, ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_INCOMPLETE_RENAME, ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.

__Request Body__:
    {
        "source" : "/tempZone/home/wregglej/test3", 
        "dest" : "/tempZone/home/wregglej/test2"
    }

__Response__:

    {
        "action":"rename",
        "source":"/tempZone/home/wregglej/test3",
        "dest":"/tempZone/home/wregglej/test2",
        "status":"success"
    }


__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"source" : "/tempZone/home/wregglej/test3", "dest" : "/tempZone/home/wregglej/test2"}' http://127.0.0.1:3000/rename?user=wregglej




