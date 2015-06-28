function makeClasses(classList) {
    return _.reduce(classList, function (a, b) {
        return a + " " + b;
    });
}

var SearchBox = React.createClass({
    getInitialState: function() {
        return { query: "" };
    },
    componentDidMount: function() {
        EVENTS.register('changeQuery', this.changeQuery);
    },
    handleKey: function(evt) {
        // submit:
        if(evt.which == 13) {
            EVENTS.signal("changeContent",
                {
                    p: "search",
                    query: React.findDOMNode(this.refs.search).value.trim()
                });
        }
    },
    changeQuery: function(query) {
        this.setState({query: query});
    },
    handleChange: function(evt) {
        this.changeQuery(evt.target.value);
    },
    render: function () {
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
    render: function() {
        console.log(this.props.links);
        var linkElems = _(this.props.links).map(function(link) {
            var href = link.url;
            if(_.isArray(link.url)) {
                href = _.first(link.url);
            }
            return <a key={link.name} className={"nav"} href={href}>{link.name}</a>
        }).value();

        linkElems.push(<SearchBox key={"search"} />);

        return <div id={"header"}>{linkElems}</div>;
    }
});

$(function() {
    var links = [
        { name: "Home", "url": ["/", "/?p=home", "/index.html"] },
        { name: "Labels", "url": "/?p=labels" }
    ];

    React.render(<NavBar links={links} />, document.getElementById("top"));
    React.render(<Globals />, document.getElementById("globals"));
});

