Replacing Spaces With Underscores
------------------------------------------------------------------------------

__URL Path__: /replace-spaces

__HTTP Method__: POST

__Action__: replace-spaces

__Error codes__: ERR_DOES_NOT_EXIST, ERR_BAD_OR_MISSING_FIELD, ERR_NOT_OWNER, ERR_NOT_A_USER

__Request query parameters__:
* user - The iRODS username for the user making the request

__Request Body__:

    {
        "paths" : [
            "/this/is a/path",
            "/this/is a/path with spaces"
        ]
    }

"paths" must contain existing paths.

__Response__:

    {
        "action" : "replace-spaces",
        "status" : "success",
        "paths" : {
            "\/this\/is a\/path" : "\/this\/is_a\/path",
            "\/this\/is a\/path with spaces" : "\/this\/is_a\/path_with_spaces"
        }
    }

__Curl Command__:

    curl -H "Content-Type:application/json" -d '{"paths" : ["/this/is a/path", "/this/is a/path with spaces"]}' http://127.0.0.1:3000/replace-spaces?user=testuser