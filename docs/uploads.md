File Upload
-----------
Uploads are now handled by iDrop Lite. Nibblonian is only responsible for generating a temporary password for a user and returning connection information.

Action: "upload"

Error codes: ERR_NOT_A_USER

Curl command:

    curl http://nibblonian.example.org/upload?user=muahaha

The response body:

    {
        "action":"upload",
        "status":"success",
        "data": {
                    "user":"muahaha",
                    "password":"c5dbff21fa123d5c726f27cff8279d70",
                    "host":"blowhole.example.org",
                    "port":1247,
                    "zone":"tempZone",
                    "defaultStorageResource":"",
                    "key":"1325877857614"
                }
    }
