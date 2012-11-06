Listing a user's group memberships
----------------------------------

This endpoint provides access to the list of a groups a given iRODS user belongs
to.

Action: "groups"
    
Error Codes: ERR_NOT_A_USER

Curl command:

    curl http://127.0.0.1:3000/groups?user=testuser

Response:

    {
        "action" : "groups",
        "status" : "success",
        "groups" : ["group1" "group2"]
    }
    