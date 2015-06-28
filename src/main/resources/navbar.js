function makeClasses(classList) {
    return _.reduce(classList, function (a, b) {
        return a + " " + b;
    });
}

var NavBar = React.createClass({
    componentWillMount: function() {

    },
    render: function() {
        console.log(this.props.links);
        var linkElems = _(this.props.links).map(function(link) {
            var href = link.url;
            if(_.isArray(link.url)) {
                href = _.first(link.url);
            }
            return <a key={link.name} className={"nav"} href={href}>{link.name}</a>
        }).value();
        console.log(linkElems);

        linkElems.push(
            <input
                key={"search"}
                className={"rightnav"}
                id={"searchBox"}
                type={"text"}
                title={"Search Sentences"}
                placeholder={"Search Sentences"}
                />
        );

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

