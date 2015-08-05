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
                   onChange={this.handleChange.bind(this)} />
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
            results: null,
            rankByPMI: false,
            leftWidth: 1,
            rightWidth: 1,
            termKind: "lemmas",
            query: "",
            request: null
        }
    }
    onFind(evt) {
        console.log(evt);
    }
    togglePMIRank() {
        this.setState({rankByPMI: !this.state.rankByPMI});
    }
    changeQuery(text) {
        this.setState({query: text});
    }
    setStateVar(name, value) {
        let delta = {};
        delta[name] = value;
        this.setState(delta)
    }
    render() {

        return <div>
            <div>Phrase Search Interface</div>
            <label>Query
                <input
                    type="text"
                    placeholder="Enter Phrase"
                    value={this.state.query}
                    onChange={(evt) => this.changeQuery(evt.target.value)}
                    /></label>

            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setStateVar("termKind", x)} />
            <div>
                <IntegerInput onChange={(x) => this.setStateVar("leftWidth", x)}
                              min={0} max={20} start={this.state.leftWidth} label="Terms on Left:" />
                <IntegerInput onChange={(x) => this.setStateVar("rightWidth", x)}
                              min={0} max={20} start={this.state.rightWidth} label="Terms on Right:" />
            </div>
            <label>Rank by PMI: <input type="checkbox" checked={this.state.rankByPMI}  onChange={() => this.togglePMIRank()} /></label>

            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>

            <pre>{JSON.stringify(this.state)}</pre>
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
    setStateVar(name, value) {
        let delta = {};
        delta[name] = value;
        this.setState(delta)
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

        // clear results:
        this.setState({
            request: request,
            response: null
        });
        postJSON("/api/matchDocuments",
            request,
            (data) => {this.setStateVar('response', data)});
    }
    render() {
        let results = "";
        if(this.state.response) {
            results = <pre key="json">{JSON.stringify(this.state.response)}</pre>;
        }

        return <div>
            <div>Document Search</div>
            <textarea value={this.state.query} onChange={(x) => this.setStateVar('query', x.target.value) } />
            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setStateVar("termKind", x)} />
            <SelectWidget opts={OperationKinds} selected={this.state.operation} onChange={(x) => this.setStateVar("operation", x)} />

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
        postJSON("/api/pullDocument",
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
        }).map((token) => {
            return token+" ";
        }).value();


        return <div>
            <label>Document #{doc.identifier}: {doc.name}</label>
            <div>{strjoin(tokens)}</div>
        </div>
    }
}