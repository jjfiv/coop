function standardErrorHandler(path, input, err) {
    console.error(path, input, err);
}
function postJSON(path, input, onDone, onErr) {
    $.ajax({
        url: path,
        type: "POST",
        data: JSON.stringify(input || {}),
        processData: false,
        contentType: "application/json",
        dataType: "json"
    }).done(onDone).error(onErr || (err => {
        standardErrorHandler(path, input, err)
    }));
}

