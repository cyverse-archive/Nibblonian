Error Codes
-----------

When it encounters an error, Nibblonian will generally return a JSON object in the form:

    {
        "action" : "general name of the service",
        "status" : "failure",
        "error_code" : "ERR_CODE"
    }
    
Other entries may be included in the map, but you shouldn't depend on them being there for error checking.

A list of the known error codes is maintained here: [error codes](https://github.com/iPlantCollaborativeOpenSource/Nibblonian/blob/master/src/nibblonian/error_codes.clj)

In other cases Nibblonian should return a stacktrace. Work is planned to convert Nibblonian's error handling over to [slingshot](https://github.com/scgilardi/slingshot) (I didn't know about slingshot when I originally wrote Nibblonian).
