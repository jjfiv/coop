function strjoin(strs) {
    return _.reduce(strs, function (a, b) {
        return a + " " + b;
    });
}

class Main {
    static index() {
        console.log("Hello World!");
        React.render(<QueryInterface />, document.getElementById("queryInterface"));
    }
    static docs() {
        React.render(<DocSearchInterface />, document.getElementById("queryInterface"));
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
            return <option id={key} value={key}>{val}</option>
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

class QueryInterface extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            results: null,
            rankByPMI: false,
            leftWidth: 1,
            rightWidth: 1,
            termKind: "lemmas",
            query: ""
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
            "termKind": "lemmas",
            "operation": "OR"
        }
    }
    setStateVar(name, value) {
        let delta = {};
        delta[name] = value;
        this.setState(delta)
    }
    onFind(evt) {
        console.log(evt);
    }
    render() {
        return <div>
            <div>Document Search</div>
            <textarea />
            <SelectWidget opts={TermKindOpts} selected={this.state.termKind} onChange={(x) => this.setStateVar("termKind", x)} />
            <SelectWidget opts={OperationKinds} selected={this.state.operation} onChange={(x) => this.setStateVar("operation", x)} />

            <Button label="Find!" onClick={(evt) => this.onFind(evt)}/>
            <pre>{JSON.stringify(this.state)}</pre>
            </div>
    }
}