{
    "fields": {
        "project": {
            "key": "${projectKey}"
        },
        "summary": "${summary}",
        "description": "${body?json_string}",
        "issuetype": {
            "name": "${issueType}"
        },
        "labels": [
            "butler",
            "test-failure"
        ]
    }
}