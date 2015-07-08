var SearchBox = React.createClass({
    getInitialState() {
        return { query: "" };
    },
    componentDidMount() {
        EVENTS.register('changeQuery', this.changeQuery);
    },
    handleKey(evt) {
        // submit:
        if(evt.which == 13) {
            var query = React.findDOMNode(this.refs.search).value.trim();
            if(_.isEmpty(query)) return;

            EVENTS.signal("searchSentences", {query: query});
            EVENTS.signal("changeContent", { p: "search", query: query });
        }
    },
    changeQuery(query) {
        this.setState({query: query});
    },
    handleChange(evt) {
        this.changeQuery(evt.target.value);
    },
    render () {
        return <input
            ref={"search"}
            className={"rightnav"}
            onKeyPress={this.handleKey}
            id={"searchBox"}
            type={"text"}
            title={"Search Sentences"}
            onChange={this.handleChange}
            value={this.state.query}
            placeholder={"Search Sentences"}
            />
    }
});

var NavBar = React.createClass({
    render() {
        console.log(this.props.links);
        var linkElems = _(this.props.links).map(function(link) {
            var href = link.url;
            if(_.isArray(link.url)) {
                href = _.first(link.url);
            }
            return <InternalLink key={link.name} styles={["nav"]} page={link.page} label={link.name} args={link.args || {}} />
        }).value();

        linkElems.push(<SearchBox key={"search"} />);

        return <div id={"header"}>{linkElems}</div>;
    }
});

$(function() {
    var links = [
        { name: "Home", "page": "home" },
        { name: "Labels", "page": "labels" },
        { name: "Tags", "page": "tags" }
    ];

    React.render(<NavBar links={links} />, document.getElementById("top"));
    React.render(<API />, document.getElementById("globals"));
});

