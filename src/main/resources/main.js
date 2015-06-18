function postJSON(url, input_data, done_fn, err_fn) {
    if(!err_fn) {
        err_fn = standardErrorHandler;
    }
    var opts = {
        url: url,
        type: "POST",
        data: JSON.stringify(input_data),
        processData: false,
        contentType: "application/json",
        dataType: "json"
    };
    $.ajax(opts).done(done_fn).error(err_fn);
}

function standardErrorHandler(err) {
    console.error(err);
}

$(function() {
    postJSON("/api/randomSentences", {}, function(response){
        var html = '';
        html += 'Finding these sentences took: '+response.time+'ms.';
        html += '<ul class="sentences">';
        console.log(response);

        var sentences = _.sortBy(response.sentences, function(s) { return s[0].sentenceId; });

        _.forEach(response.sentences, function(s) {
            html += '<li>';
            html += '<span class="sentenceId">'+ s[0].sentenceId + '</span> ';

            _.forEach(s, function(token) {
                var title = _(token.terms).map(function(v, k) {
                    if(k.contains("ner") && v == "O") return "";
                    return k+"="+v;
                }).reject(_.isEmpty).join('; ');

                html += '<span title="'+title+'" class="token">'+ token.terms.true_terms + '</span> ';
            });
            html += '</li>';
        });

        html += '</ul>';
        $('#sentences').html(html);
    });
});

