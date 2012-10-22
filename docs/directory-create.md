Directory Creation
------------------
Action: "create"

Error codes: ERR_NOT_WRITEABLE, ERR_EXISTS, ERR_NOT_A_USER

Curl command:

    curl -H "Content-Type:application/json" -d '{"path" : "/tempZone/home/rods/test3"}' http://127.0.0.1:3000/directory/create

The response body:

    {
       "action":"create",
       "path":"\/tempZone\/home\/rods\/test3",
       "status":"success"
    }

