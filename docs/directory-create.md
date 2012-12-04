Directory Creation
------------------
__URL Path__: /directory/create

__HTTP Method__: POST

__Action__: create

__Error codes__: ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_NOT_A_USER

__Request Query Parameters__:
* user - The iRODS username of the user making the request.

__Request Body__:

    {
        "path" : "/tempZone/home/rods/test3"
    }

__Response__:

    {
       "action":"create",
       "path":"\/tempZone\/home\/rods\/test3",
       "status":"success"
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"path" : "/tempZone/home/rods/test3"}' http://127.0.0.1:3000/directory/create




