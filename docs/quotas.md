Listing a user's quotas
-----------------------

Action: "quota-list"

Error Codes: ERR_NOT_A_USER

Curl command:

    curl http://127.0.0.1:3000/quota?user=testuser
    
Response:

    {
        "action" : "quota-list",
        "status" : "success",
        "quotas" : [
            {
                "zone" : "iplant",
                "resource" : "demoResc",
                "over" : "-1000000",
                "updated" : "1341611109000",
                "limit" : "1000000",
                "user" : "testuser"
            }
        ]
    }

