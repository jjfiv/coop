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

var Sentence = React.createClass({
    getSentenceId: function() {
        return this.props.tokens[0].sentenceId;
    },
    render: function() {
        var listNodes = this.props.tokens.map(function(token) {
            return <Token terms={token.terms} />;
        });
        return <span className="sentence"><span className="sentenceId">{this.getSentenceId()}</span>{listNodes}</span>;
    }
});


var Token = React.createClass({
    computeTitle: function() {
        return _(this.props.terms).map(function(v, k) {
            if(k.contains("ner") && v == "O") return "";
            return k+"="+v;
        }).reject(_.isEmpty).join('; ')
    },
    getTerm: function() {
        // grab CoNNL-specific "true_terms"
        return this.props.terms.true_terms;
    },
    render: function() {
        return <span className="token" title={this.computeTitle()}>{this.getTerm()}</span>;
    }
});

var SentenceList = React.createClass({
    render: function() {
        var sTags = _(this.props.sentences)
            .map(function(tokens) { return <Sentence tokens={tokens} />; })
            .sortBy('getSentenceId')
            .map(function (tag) { return <li>{tag}</li>; })
            .values();
        return <ul className="sentences">{sTags}</ul>;
    }
});

var RandomSentences = React.createClass({
    getInitialState: function() {
        return {
            requestCount: this.props.requestCount || 5,
            response: {},
            waiting: true,
            error: null
        };
    },
    refreshData: function() {
        this.setState({response: {}, waiting: true, error: null});
        postJSON("/api/randomSentences", {
            count: this.state.requestCount
        }, function(data) {
            this.setState({response: data, error: null, waiting: false});
        }.bind(this), function (err) {
            this.setState({error: err, response: {}, waiting: false})
        }.bind(this))
    },
    componentDidMount: function() {
        this.refreshData();
    },
    render: function() {
        if(this.state.waiting) {
            return <div>Waiting for server response.</div>;
        } else if(this.state.error != null) {
            return <AjaxError err={this.state.error} />;
        } else {
            return <div>
                {"Finding these "+ _.size(this.state.response.sentences)+ " sentences took "+this.state.response.time+"ms. "}
                <SentenceList sentences={this.state.response.sentences} />
            </div>;
        }
    }
});


$(function() {
    React.render(<RandomSentences requestCount={50} />, document.getElementById("sentences"));
/*postJSON("/api/randomSentences", {}, function(response){
 var html = '';
 html += 'Finding these sentences took: '+response.time+'ms.';
 html += React.renderToStaticMarkup(<SentenceList sentences={response.sentences} />);
 $('#sentences').html(html);
 });*/
});

