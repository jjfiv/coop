// Global user:
var globalUser = window.localStorage.getItem("user");

class Main {
    static init() {
    }
    static render(what) {
        React.render(what,document.getElementById("content"));
    }
    static index() {
        Main.init();
        Main.render(<UserInterface />);
    }
}

class UserInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            user: window.localStorage.getItem("user"),
            text: ""
        }
    }
    tryLogin() {
        if(this.state.text != "") {
            let user = this.state.text;
            window.localStorage.setItem("user", user);
            this.setState({user});
            globalUser = user;
        }
    }
    logout() {
        this.setState({text:globalUser, user:null});
        globalUser = null;
    }
    render() {
        let user = this.state.user;
        if(user == null) {
            return <div className="loginForm">Choose a user id:
                <input value={this.state.text}
                       onChange={(evt) => this.setState({text: evt.target.value})}
                       onKeyPress={(evt) => (evt.which == 13) ? this.tryLogin() : null }
                       type="text" />
                <Button label="Login" onClick={(evt) => this.tryLogin()} />
            </div>
        } else {
            return <div>
                <div className="loggedInMessage">Logged in as user <strong>{user}</strong>.
                    <Button label="Logout" onClick={(evt) => this.logout()} /> </div>
                <QueryWriter />
                </div>;
        }
    }
}

class QueryWriter extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            history: [],
            rand: null
        }
    }
    componentDidMount() {
        let urlP = getURLParams();
        if(urlP.id) {
            let id = parseInt(urlP.id);
            postJSON("/api/fact", {id}, (succ) => this.setState({rand:succ}));
        } else {
            // random:
            this.requestNew();
        }
    }
    requestNew() {
        if(this.state.rand != null) {
            this.setState({rand: null});
        }
        postJSON("/api/rand", {}, (rand) => {
            this.setState({rand});
            pushURLParams({id:rand.id});
        });
    }
    render() {
        let fact = this.state.rand;
        let current;
        if(fact) {
            current = <FactRenderer fact={fact} />;
        } else {
            current = <i>Waiting for the server...</i>;
        }

        return <div>{current}
            <Button label={"Next Fact"} onClick={(evt) => this.requestNew()} />
        </div>

    }
}

function pushFront(arr, item) {
    let new_arr = [];
    new_arr.push(item);
    let i = 0;
    for(i = 0; i < _.size(arr); i++) {
        new_arr.push(arr[i]);
    }
    return new_arr;
}

function showQuery(q) {
    if(globalUser == "jfoley") {
        return true;
    } else {
        return q.user == globalUser && !q.deleted;
    }
}

class QuerySuggestions extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            text: "",
            fact: props.fact,
            interval: null,
            refreshing:false,
            queries: _.filter(props.fact.queries, showQuery)
        }
    }
    componentWillReceiveProps(props) {
        if(props.fact.id != this.state.fact.id) {
            this.setState({text:""})
        }
        this.refresh(props.fact);
    }
    refresh(fact) {
        this.setState({fact:fact, refreshing:false,queries: _.filter(fact.queries, showQuery)})
    }
    requestRefresh() {
        this.setState({refreshing:true});
        postJSON("/api/fact", {id: this.props.fact.id}, (fact) => this.refresh(fact))
    }
    componentDidMount() {
        // setup auto-refresh...
        this.setState({interval: setInterval(() => this.requestRefresh(), 5000)})
    }
    componentWillUnmount() {
        if(this.state.interval) {
            window.clearInterval(this.state.interval);
            this.setState({interval:null});
        }
    }
    submit() {
        let text = this.state.text.trim();
        if(_.isEmpty(text)) {
            return;
        }
        this.setState({text:""});
        postJSON("/api/suggestQuery", {factId: this.props.fact.id, user: globalUser, query:text},
            (response) => this.setState({queries: pushFront(this.state.queries, response)})
        );
    }
    deleteQuery(queryId) {
        postJSON("/api/deleteQuery", {factId: this.props.fact.id, queryId});
        requestRefresh();
    }
    render() {
        let queries = _(this.state.queries).map(q => {
            console.log(globalUser);
            if(globalUser === "jfoley") {
                var classes = ["querySuggest"];
                if(q.deleted) {
                    classes.push("deleted");
                }
                return <div className={strjoin(classes)} key={q.id}>
                    {q.id}. <b>{q.query}</b> by <i>{q.user}</i> at <TimeDisplay time={q.time} />
                    {q.deleted ? <span className="deletedAt"> Deleted at <TimeDisplay time={q.deleted} /></span>: <Button label="Delete" onClick={(evt) => this.deleteQuery(q.id)}/>}
                </div>;
            } else {
                return <div className={"querySuggest"} key={q.id}>{q.query} <Button label="Delete" onClick={(evt) => this.deleteQuery(q.id)}/></div>;
            }
        }).value();
        _.sortBy(queries, (x) => -x.time);
        let next = <div>
            <input
                value={this.state.text}
                onChange={(evt) => this.setState({text:evt.target.value})}
                onKeyPress={(evt) => (evt.which == 13) ? this.submit() : null}
                type="text"/>
            <Button label="Suggest Query" onClick={(evt) => this.submit()} />
        </div>;
        return <div>{next}<div>{queries}</div></div>;
    }
}

class TimeDisplay extends React.Component {
    render() {
        let dt = new Date(this.props.time);
        return <span>{dt.toLocaleString()}</span>;
    }
}

class FactRenderer extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            open: !this.props.history
        }
    }
    render() {
        let editable = this.props.history;
        let fact = this.props.fact;
        return <div>
            <div>Fact ID: #<i>{fact.id}</i></div>
            <div className="indent">In <strong>{fact.year}</strong>, <span dangerouslySetInnerHTML={{__html: fact.html}} /></div>
            {editable ? <label key={"esq"+fact.id}>Suggest Queries
            <input type="checkbox" checked={this.state.open} onChange={() => this.setState({open: !this.state.open})} /></label> : null}
            {this.state.open ? <QuerySuggestions key={"qs"+fact.id} fact={fact} /> : null}
        </div>
    }
}

