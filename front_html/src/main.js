function strjoin(strs) {
    return _.reduce(strs, function (a, b) {
        return a + " " + b;
    });
}

function getURLParams() {
    var match,
        pl = /\+/g, // Regex for replacing addition symbol with a space
        search = /([^&=]+)=?([^&]*)/g,
        decode = function(s) {
            return decodeURIComponent(s.replace(pl, " "));
        },
        query = window.location.search.substring(1);

    var urlParams = {};
    while ((match = search.exec(query))) {
        var key = decode(match[1]);
        var value = decode(match[2]);
        if (value === "null") {
            value = null;
        } else if (value === "true") {
            value = true;
        } else if (value === "false") {
            value = false;
        }
        // it's possible there are multiple values for things such as labels
        if (_.isUndefined(urlParams[key])) {
            urlParams[key] = value;
        } else {
            // urlParams[key] += "&" + key + "=" + value;
            urlParams[key] += "," + value;
        }
    }
    return urlParams;
}

class Main {
    static render(what) {
        React.render(what,document.getElementById("queryInterface"));
    }
    static index() {
        Main.render(<QueryInterface />);
    }
    static docs() {
        Main.render(<DocSearchInterface />);
    }
    static doc() {
        Main.render(<DocViewInterface urlParams={getURLParams()} />);
    }
}

/**
 * Usage: <Button visible={true,false} disabled={true,false} onClick={whatFn} label={text label} />
 */
class Button extends React.Component{
    render() {
        var classes = [];
        classes.push(this.props.visible ? "normal" : "hidden");
        return <input
            className={strjoin(classes)}
            disabled={this.props.disabled}
            type={"button"}
            title={this.props.title || this.props.label}
            onClick={this.props.onClick}
            value={this.props.label}
            />;
    }
}
Button.defaultProps = {
    visible: true,
    disabled: false
};

class IntegerInput extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            text: ""+this.props.start,
            validInt: true
        };
    }
    handleChange(evt) {
        var x = parseInt(evt.target.value);
        if(_.isNaN(x)) {
            this.setState({
                validInt: false,
                text: evt.target.value.trim().toLowerCase()
            });
            return;
        }
        if(x < this.props.min) { x = this.props.min; }
        if(x > this.props.max) { x = this.props.max; }
        this.setState({validInt: true, text: ""+x});
        this.props.onChange(x);
    }
    render() {
        let cls = [];
        if(!this.state.validInt) {
            cls.push("negative")
        }
        return <span>
            {this.props.label}
            <input type={"text"}
                   className={strjoin(cls)}
                   style={{width:"4em"}}
                   value={this.state.text}
                   onChange={evt => this.handleChange(evt)} />
        </span>
    }
}

class SelectWidget extends React.Component {
    render() {
        let ropts = _.map(this.props.opts, (val, key) => {
            return <option key={key} value={key}>{val}</option>
        });
        return <select onChange={(evt) => this.props.onChange(evt.target.value)}
                       value={this.props.selected}
            >{ropts}</select>;
    }
}
const TermKindOpts = {
    "pos": "Part of Speech",
    "lemmas": "Lemma or Stem",
    "tokens": "Tokens"
};

function standardErrorHandler(err) {
    console.error(err);
}
function postJSON(path, input, onDone, onErr) {
    $.ajax({
        url: path,
        type: "POST",
        data: JSON.stringify(input),
        processData: false,
        contentType: "application/json",
        dataType: "json"
    }).done(onDone).error(onErr || standardErrorHandler);
}

class QueryInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            leftWidth: 1,
            rightWidth: 1,
            termKind: "lemmas",
            query: "",
            count: 200,
            error: false,
            request: null,
            response: null
        }
    }
    onFind(evt) {
        // one request at a time...
        if(this.searching()) return;

        let request = {};
        request.termKind = this.state.termKind;
        request.count = this.state.count;
        request.query = this.state.query;

        // clear results:
        this.setState({
            request: request,
            response: null
        });
        postJSON("/api/FindPhrase",
            request,
            (data) => {this.setState({
                error: false,
                response: data
            })},
            (data) => {this.setState({
                error: true,
                response: data
            })}
        );
    }
    searching() {
        return this.state.response == null && this.state.request != null;
    }
    render() {
        return <div>
            <div>Phrase Search Interface</div>
            <label>Query
                <input
                    type="text"
                    placeholder="Enter Phrase"
                    value={this.state.query}
                    onChange={(evt) => this.setState({query: evt.target.value})}
                    /></label>

            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setState({termKind: x})} />
            <div>
                <IntegerInput onChange={(x) => this.setState({leftWidth: x})}
                              min={0} max={20} start={this.state.leftWidth} label="Terms on Left:" />
                <IntegerInput onChange={(x) => this.setState({rightWidth: x})}
                              min={0} max={20} start={this.state.rightWidth} label="Terms on Right:" />
            </div>
            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <PhraseSearchResults error={this.state.error} request={this.state.request} response={this.state.response} />
            </div>;
    }
}

class QueryDisplay extends React.Component {
    render() {
        let text = this.props.text;
        let kind = this.props.kind;
        let terms = this.props.terms;

        let term_tags = _.map(terms, (term, idx) => <span key={idx} className="token">{term + " "}</span>);

        return <span>Query "{text}" [{TermKindOpts[kind]}] <span>{term_tags}</span></span>;
    }
}

class DocumentLink extends React.Component {
    render() {
        let id = this.props.id;
        let name = this.props.name;
        return <a href={"/doc.html?id="+id}>{"#"+id+" "+name}</a>
    }
}

class PhraseSearchResults extends React.Component {

    render() {
        let req = this.props.request;
        let resp = this.props.response;

        if(resp == null) {
            if(req != null) {
                return <div>Searching...</div>;
            }
            return <div></div>;
        }

        if(this.props.error) {
            return <div>{resp.responseText}</div>;
        }

        let results = _(resp.results).map((x, idx) => {
            return <li key={idx}><DocumentLink id={x.id} name={x.name} /> {JSON.stringify(x)}</li>
        }).value();

        return <div>
            <div>Found {resp.queryFrequency} results for <QueryDisplay text={req.query} kind={req.termKind} terms={resp.queryTerms} />.</div>
            <ul>{results}</ul>
        </div>;
    }
}

const OperationKinds = {
    "OR": "Any Terms (OR)",
    "AND": "All Terms (AND)"
};
class DocSearchInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            termKind: "lemmas",
            query: "the president",
            operation: "OR",
            request: null,
            response: null
        }
    }
    searching() {
        return this.state.request != null;
    }
    onFind(evt) {
        // one request at a time...
        if(this.searching()) return;

        let request = {};
        request.termKind = this.state.termKind;
        request.query = this.state.query;
        request.operation = this.state.operation;

        console.log(request);
        postJSON("/api/MatchDocuments",
            request,
            (data) => {this.setState({response: data})});

        // clear results:
        this.setState({
            request: request,
            response: null
        });
    }
    render() {
        let results = "";
        if(this.state.response) {
            results = <pre key="json">{JSON.stringify(this.state.response)}</pre>;
        }

        return <div>
            <div>Document Search</div>
            <textarea value={this.state.query} onChange={(x) => this.setState({query: x.target.value}) } />
            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setState({termKind: x})} />
            <SelectWidget opts={OperationKinds} selected={this.state.operation} onChange={(x) => this.setState({operation: x})} />

            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <DocumentResults response={this.state.response} />
            </div>
    }
}

class DocumentResults extends React.Component {
    render() {
        let resp = this.props.response;
        if(resp == null) {
            return <span />;
        }

        let results = _(resp.results).map(function(obj) {
            return <li key={obj.id}><a href={"/doc.html?id="+obj.id}>{"#"+obj.id+" "+obj.name}</a></li>
        }).value();

        return <div>
            <label>Query Terms: <i>{strjoin(resp.queryTerms)}</i></label>
            <ul>{results}</ul>
            </div>;
    }
}

class DocViewInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            docId: parseInt(props.urlParams.id) || 0,
            response: null
        }
    }
    componentDidMount() {
        postJSON("/api/PullDocument",
            {id: this.state.docId},
            (data) => this.setState({response: data}));
    }
    render() {
        let doc = this.state.response;
        if (doc == null) {
            return <div>Loading...</div>;
        }

        let tokens = _(doc.terms.tokens).map((x) => {
            switch(x) {
                case "-LSB-": return "[";
                case "-RSB-": return "]";
                case "-LRB-": return "(";
                case "-RRB-": return ")";
                default: return x;
            }
        }).map((token, idx) => {
            if(_.startsWith(token, "______")) {
                return <hr key={idx} id={"t"+idx} />
            }
            return <span className={"token"} key={idx} id={"t"+idx}>{token+" "}</span>;
        }).value();

        var sentences = _.map(doc.tags.sentence, (extent) => {
            var begin = extent[0];
            var end = extent[1];
            return <span className={"sentence"}>{
                _.slice(tokens, begin, end)
            }</span>;
        });

        return <div>
            <label>Document #{doc.identifier}: {doc.name}</label>
            <div>{sentences}</div>
        </div>
    }
}

