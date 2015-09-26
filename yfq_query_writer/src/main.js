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
                    <Button label="Logout" onClick={(evt) => this.logout()} />
                    {admin() ? <Button label="Save!" onClick={(evt) => postJSON("/api/save")} />  : null}
                </div>
                <QueryWriter />
                </div>;
        }
    }
}

class RecommendedJudgments extends React.Component {
    render() {
        let getText = (x) => {
            return x.users[globalUser] || "Fact #"+x.factId;
        };
        let render = (x) => {
            let id = x.factId;
            return <a className={"recommended"} key={id} href={"/?id="+id}>{getText(x)}</a>
        };

        return <FilterableList
            getItemText={x => render(x)}
            items={this.props.judged}
            getItemText={getText}
            keyFn={x => x.factId}
            renderItem={render} />;
    }
}

class QueryWriter extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            rand: null,
            judged: []
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
        this.refreshJudged();
    }
    refreshJudged() {
        postJSON("/api/judged", {}, (what) => this.setState({judged: what.judged}));
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
    setFact(fact) {
        this.setState({rand:fact});
        pushURLParams({id:fact.id});
    }
    render() {
        let fact = this.state.rand;
        let current;
        if(fact) {
            current = <FactRenderer refresh={(fact) => { this.setFact(fact) }} nextRandom={() => this.requestNew()} fact={fact} />;
        } else {
            current = <i>Waiting for the server...</i>;
        }

        return <div>
            <div>
                <div>Already-Edited Facts: <Button label="Refresh" onClick={(evt) => this.refreshJudged()} /></div>
                <RecommendedJudgments judged={this.state.judged}  />
            </div>
            {current}
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

function admin() {
    return globalUser == "jfoley";
}
function showEntity(e) {
    return e.user == "WIKI-YEAR-FACTS" || showQuery(e);
}

function showQuery(q) {
    if(admin()) {
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
        }
    }
    componentWillReceiveProps(props) {
        if(props.fact.id != this.state.fact.id) {
            this.setState({text:""})
        }
        this.refresh(props.fact);
    }
    refresh(fact) {
        this.setState({fact:fact, refreshing:false})
    }
    submit() {
        let text = this.state.text.trim();
        if(_.isEmpty(text)) {
            return;
        }
        this.setState({text:""});
        postJSON("/api/suggestQuery", {factId: this.props.fact.id, user: globalUser, query:text}, (fact) => this.refresh(fact));
    }
    deleteQuery(queryId) {
        postJSON("/api/deleteQuery", {factId: this.props.fact.id, queryId}, (fact) => this.refresh(fact));
    }
    render() {
        let fact = this.state.fact;
        let queries = _(fact.queries).filter(showQuery).map(q => {
            if(admin()) {
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

function showUserName(userName) {
    if(userName == globalUser) {
        return "you";
    } else {
        return userName;
    }
}

class RelevanceChoice extends React.Component {
    render() {
        let score = this.props.score;
        let id = this.props.id;

        let data = [
                {desc: "Not Relevant", score: 0},
                {desc: "Probably Not Relevant", score: 1},
                {desc: "Maybe Relevant", score: 2},
                {desc: "Probably Relevant", score: 3},
                {desc: "Relevant", score: 4}
            ];

        let radios = _.map(data, (item) => {
            return <label key={item.score} className={"rel"}><input type="radio" name="rel" checked={item.score == score} onChange={(evt) => {
                this.props.onRating(id, item.score);
            }}/>{item.desc}</label>
        });

        return <form>{radios}</form>;
    }
}

class EntityRenderer extends React.Component {
    render() {
        let entity = this.props.entity;
        let latest_judgment = _.first(_.sortBy(_.filter(entity.judgments, showEntity), (x) => -x.time));
        let is_fake_judgment = latest_judgment.time == 0;
        return <div>
            <a href={"https://en.wikipedia.org/wiki/"+entity.name}>{entity.name}</a>
            &nbsp;
            {(is_fake_judgment) ? "(above)" : <span>{"Marked by "+showUserName(latest_judgment.user)+" at "}<TimeDisplay time={latest_judgment.time} /></span>}
            &nbsp;
            <RelevanceChoice score={is_fake_judgment ? 3 : latest_judgment.relevance} onRating={(id, score) => this.props.submitRating(id, 0, score)} id={entity.name} />
            </div>
    }
}

class FactRenderer extends React.Component {
    submitRating(id, qId, score) {
        postJSON("/api/judgeEntity", {
            factId: this.props.fact.id,
            queryId: qId,
            user: globalUser,
            relevance: score,
            entity: id },
            (succ) => {
                this.refresh(succ);
            }
        )
    }
    refresh(fact) {
        this.props.refresh(fact);
    }
    render() {
        let onNextFact = this.props.onNextFact;
        let fact = this.props.fact;

        let qs = <QuerySuggestions key={"qs"+fact.id} fact={fact} />;
        //let entities = <pre>{JSON.stringify(fact.entities, null, 2)}</pre>;

        let entities = _(fact.entities).map((val, key) => {
            let e = {
                factId: fact.id,
                name: key,
                judgments: val
            };
            return <EntityRenderer key={key} entity={e} submitRating={(id, qid, score) => this.submitRating(id, qid, score)}/>;
        }).value();

        return <div>
            <div className="fact-display">
                <Button label={"Next Random Fact"} onClick={(evt) => this.props.nextRandom()} />
                {(admin() ? <div>Fact ID: #<i>{fact.id}</i></div> : null)}
                <div className="indent">In <strong>{fact.year}</strong>, <span dangerouslySetInnerHTML={{__html: fact.html}} /></div>
            </div>
            <table className={"factRendererTable"} border={1}>
                <tr><th>Queries</th><th>Entities</th></tr>
                <tr><td>{qs}</td><td>{entities}</td></tr></table>
        </div>
    }
}

