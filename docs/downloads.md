File Download
-------------
Downloads are now handled by iDrop Lite. Nibblonian handles serializing the shopping cart object and returning a temporary password.

Action: "download"

Error codes: ERR_NOT_A_USER

Curl command:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/tempZone/home/muahaha/test.txt"]}' 'http://nibblonian.example.org/download?user=muahaha'

The response:

    {
        "action":"download",
        "status":"success",
        "data": {
                    "user":"muahaha",
                    "password":"cc181a5a97635c7b45a3b2b828f964fe",
                    "host":"blowhole.example.org",
                    "port":1247,
                    "zone":"tempZone",
                    "defaultStorageResource":"",
                    "key":"1325878326128"
                }
    }
