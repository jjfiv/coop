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
    static page() {
        Main.init();
        Main.render(<PageView />);
    }
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

let userPrompt = <span>
    <h1>Instructions</h1>
    <p>Imagine that you are browsing the internet, and you come across the following statement or fact. You read it, and maybe some of the associated pages, and discover that you want to know more. </p>
    <p><strong>Please suggest 2-3 queries that you might submit to a search engine.</strong> The queries you suggest are saved automatically; so there is no submit button.</p>
    <p>These facts are drawn from the Wikipedia year pages, like the one for <a href="http://en.wikipedia.org/wiki/2015">2015</a>. If you cannot determine what the fact is about for any reason, please do not suggest any queries.</p>
    <p className="needsQueries">RED facts have no queries by any author.</p>
    <p className="needsMyQueries">BLUE facts have queries by at least one author.</p>
    <p className="doneMyQueries">GREEN facts have been completed by your user name.</p>
    <p><strong>Thank you!</strong> You can click on the <span className="needsQueries">red</span> and <span className="needsMyQueries">blue</span> ones you haven't done yet, or press Random to see where it takes you.</p>
</span>;


class QuerySuggestionUI extends React.Component {
    constructor(props) {
        super(props);
        let urlP = getURLParams()
        this.state = {
            facts: [],
            activeFact: parseInt(urlP.id) || 0,
            queryText: "",
        }
    }
    componentDidMount() {
        postJSON("/api/facts", {}, (succ) => this.setState({
            facts: succ.facts,
        }));
    }
    refreshFacts() {
        postJSON("/api/facts", {}, (succ) => this.setState({facts: succ.facts}));
    }
    setActive(activeFact) {
        pushURLParams({id:activeFact})
        this.setState({activeFact});
    }
    suggestQuery(fact) {
        let query = this.state.queryText.trim();
        if(_.isEmpty(query)) return;
        postJSON("/api/suggestQuery", {factId: fact.id, user: globalUser, query}, succ => { this.setState({facts: succ.facts}) })
    }
    deleteQuery(factId, queryId) {
        postJSON("/api/deleteQuery", {factId, queryId}, succ => { this.setState({facts: succ.facts}) })
    }
    render() {
        let activeIndex = this.state.activeFact;
        let facts = this.state.facts;

        if(!facts) {
            return <div>Loading...</div>;
        }

        let colorClassForFact = (fact) => {
            // others
            // in summary, even hide deleted from admin
            let any_queries = _(fact.queries).filter(
                    q => !q.deleted
            ).value();
            // mine
            let my_queries = _(any_queries).filter(showQuery).value();
            /*let judgments = _(fact.judgments).filter((judgment) => {
             // keep only your own judgments:
             return (judgment.time > 0) && (admin() || judgment.user == globalUser);
             }).groupBy(j => j.item).value();*/

            let needsMyQueries = _.isEmpty(my_queries);
            let needsQueries = _.isEmpty(any_queries);
            //let needsJudgments = _.isEmpty(judgments);

            if(needsQueries) {
                return "needsQueries";
            } else if(needsMyQueries) {
                return "needsMyQueries";
            } else {
                return "doneMyQueries";
            }
        };

        let summary = _(facts).map((fact, index) => {
            let current = index == activeIndex;
            let classes = ["summary"];
            if(current) {
                classes.push("current");
            } else {
                classes.push(colorClassForFact(fact));
            }

            return <Button key={index} classes={classes} label={(index+1)} disabled={current} onClick={e=>this.setActive(index)} />
        }).value();

        let fact = facts[this.state.activeFact];
        let contents = <div>Loading...</div>;
        if(fact) {
            let yourQueries = _(fact.queries).filter(showQuery).map((q) => {
                let classes = ["queryView"];
                if(q.deleted) {
                    classes.push("deleted");
                }
                return <p className={strjoin(classes)} key={q.id}>{q.query}
                    <Button visible={!q.deleted} onClick={e=>this.deleteQuery(fact.id, q.id)} label={"Delete"}  />
                </p>;
            }).value();

            contents = <div>
                <p className="fact">
                    <strong className={colorClassForFact(fact)}>Fact #{activeIndex+1}:</strong> <SimpleFactRenderer fact={fact} />
                </p>
                <p>
                    <input className="querySuggestBox"
                           value={this.state.queryText}
                           onChange={evt => this.setState({queryText: evt.target.value})}
                           onKeyPress={(evt) => (evt.which == 13) ? this.suggestQuery(fact) : null }
                           type="text"/>
                    <Button label="Suggest Query" onClick={e=>this.suggestQuery(fact)} />
                </p>
                <div>{yourQueries}</div>
            </div>;
        }

        summary.push(<Button label="Random" onClick={(e=>this.setActive((Math.random()*_.size(facts)) | 0))} />);

        return <div>
            <div>{userPrompt}</div>
            <hr />
            <h1>Available Facts</h1>
            {summary}
            <hr />
            <h1>Current Fact</h1>
            {contents}
        </div>;
    }
}

class SimpleFactRenderer extends React.Component {
    render() {
        let fact = this.props.fact;
        return <span>In <a href={"http://en.wikipedia.org/wiki/"+fact.year}>{fact.year}</a>, <span dangerouslySetInnerHTML={{__html: fact.html}} /></span>;
    }
}

class PageView extends React.Component {
    constructor(props) {
        super(props);
        let urlP = getURLParams();
        this.state = {
            id: parseInt(urlP.id) || 0
        }
    }
    componentDidMount() {
        this.loadPage(this.state.id, true);
    }
    loadPage(id, force) {
        if(force || id != this.state.id) {
            this.setState({id, data: null});
            pushURLParams({id: id});
            postJSON("/api/page", {id}, (data) => {
                this.setState({data})
            })
        }
    }
    changePage(delta) {
        let newId = Math.max(this.state.id + delta, 0);
        this.loadPage(newId);
    }
    static pageImage(archiveId, pageNum) {
        return "http://www.archive.org/download/" + encodeURIComponent(archiveId) + "/page/n" + pageNum + ".jpg";
    }
    static pageThumbnail(archiveId, pageNum) {
        return "http://www.archive.org/download/" + encodeURIComponent(archiveId) + "/page/n" + pageNum + "_thumb.jpg";
    }
    render() {

        let page = this.state.data;

        let id = "???";
        let pageNo = 0;

        let pageContents;
        if(!page) {
            pageContents = <div>Loading...</div>;
        } else {
            name = page.name;
            id = name.split(":")[0];
            pageNo = parseInt(name.split(":")[1])+1;

            let text = strjoin(page.terms);

            //pageContents = <pre>{JSON.stringify({id, pageNo, text},null,2)}</pre>;
            pageContents = text;
        }

        return <div>
            <div>
                <Button label="<<" onClick={e=> this.changePage(-1)} />
                <b>{id}</b> pp. {pageNo}
                <Button label=">>" onClick={e=> this.changePage(+1)} />
            </div>
            <table>
                <tr>
                    <td><img className={"pageImage"} src={PageView.pageImage(id, pageNo-1)} /></td>
                    <td>{pageContents}</td>
                </tr>
            </table>
        </div>;
    }
}

class UserInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            user: window.localStorage.getItem("user") || null,
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
        window.localStorage.clear();
    }
    render() {
        let instructions = <div>
            <p>We recommend using your email as a login id, but if you wish to be anonymous, you may choose something that does not reflect your name, as long as it is consistent.</p>
        </div>;
        let user = this.state.user;
        if(user == null) {
            return <div className="loginForm">{instructions}<hr />
                <center>
                    Choose a user id:
                    <input className="loginBox" value={this.state.text}
                           onChange={(evt) => this.setState({text: evt.target.value})}
                           onKeyPress={(evt) => (evt.which == 13) ? this.tryLogin() : null }
                           type="text" />
                    <Button label="Login" onClick={(evt) => this.tryLogin()} />
                </center>
            </div>
        } else {
            return <div>
                <div className="loggedInMessage">Logged in as user <strong>{user}</strong>.
                    <Button label="Logout" onClick={(evt) => this.logout()} />
                    {admin() ? <Button label="Save!" onClick={(evt) => postJSON("/api/save")} />  : null}
                </div>
                <QuerySuggestionUI />
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
    submitPageRating(id, qId, score) {
        postJSON("/api/judgePage", {
                factId: this.props.fact.id,
                queryId: qId,
                user: globalUser,
                relevance: score,
                page: id },
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
            <table className={"factRendererTable"}>
                <tr><th>Queries</th></tr>
                <tr><td>{qs}</td></tr>
            </table>
            <table>
                <tr><th>Pages</th></tr>
                <tr><td>{fact.pages}</td></tr>
                <tr><th>PageSearch Search</th></tr>
                <tr><td><PageSearch submitRating={(id, qid, score) => this.submitPageRating(id, qid, score)} /></td></tr>
            </table>
        </div>
    }
}

class EntitySearch extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            text: "",
        }
    }
    submitQuery() {
        let q = this.state.text.trim();
        if(_.isEmpty(q)) return;
        console.log(q);
        postJSON("/api/searchEntities", {query: q, pullTerms: false, n:30}, (succ) => {
            this.setState({entities: succ.docs});
        })
    }
    render() {
        let getText = (x) => x.name;
        let render = (x) => {
            let id = x.name;
            return <span key={id}>
                    <a href={"http://en.wikipedia.org/"+id}>{id}</a>&nbsp;
                    <a href={"http://dbpedia.org/page/"+id}>@Dbpedia</a>
                    <RelevanceChoice score={-1} onRating={(id, score) => this.props.submitRating(id, 0, score)} id={x.name} />
                </span>
        };

        return <div>
            <div><input value={this.state.text}
                        type="text"
                        onKeyPress={(evt) => (evt.which == 13) ? this.submitQuery() : null }
                        onChange={(evt) => this.setState({text: evt.target.value})} />
                <Button label="Search" onClick={e=>this.submitQuery()} /></div>
            <FilterableList
                getItemText={render}
                items={this.state.entities}
                getItemText={getText}
                keyFn={getText}
                renderItem={render} />
            </div>;
    }
}

class PageSearch extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            text: "",
            data: {}
        }
    }
    submitQuery() {
        let q = this.state.text.trim();
        if(_.isEmpty(q)) return;
        console.log(q);
        this.setState({data:{}});
        postJSON("/api/searchPages", {query: q, pullTerms: true, n:30}, (succ) => {
            this.setState({data: succ});
        })
    }
    render() {
        let data = this.state.data || {};
        let queryTerms = _.map(data.queryTerms || [], (x) => x.toLowerCase());
        let docs = data.docs || [];

        let getText = (x) => x.name;
        let render = (x) => {
            let name = x.name;
            let id = name.split(":")[0];
            let pageIndex = parseInt(name.split(":")[1]);
            let pageNo = pageIndex+1;

            //let snippet = strjoin(x.terms);

            let snippet = _.map(x.terms, (term) => {
                if(_.contains(queryTerms, term)) {
                    return [<strong>{term}</strong>, ' ']
                } else {
                    return [term, ' '];
                }
            });

            return <span key={id}>
                    <a href={"/page.html?name="+name}>{id} pp.{pageNo}</a>&nbsp;
                <a href={"http://archive.org/details/"+id}>@Internet Archive</a>
                <div>{snippet}</div>
                    <RelevanceChoice score={-1} onRating={(id, score) => this.props.submitRating(id, 0, score)} id={x.name} />
                </span>;
        };

        return <div>
            <div><input value={this.state.text}
                        type="text"
                        onKeyPress={(evt) => (evt.which == 13) ? this.submitQuery() : null }
                        onChange={(evt) => this.setState({text: evt.target.value})} />
                <Button label="Search" onClick={e=>this.submitQuery()} /></div>
            <FilterableList
                getItemText={render}
                items={docs}
                getItemText={getText}
                keyFn={getText}
                renderItem={render} />
        </div>;
    }
}
