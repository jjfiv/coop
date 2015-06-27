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
            var url = window.location.href;
            var host = window.location.host;
            var offset = url.indexOf(host);

            console.log(url.substr(offset + host.length));
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
        { name: "Home", "url": ["/", "/index.html"] },
        { name: "Labels", "url": "/labels.html" }
    ];

    React.render(<NavBar links={links} />, document.getElementById("top"));
});

/*<div id="header">
    <a class="nav" href="#">Home</a>
    <a class="nav" href="#">Search</a>
    <a class="nav" href="#">Explore</a>
    <a class="active nav" href="#">Labels</a>
</div>
<div id="subheader">
    <a class="subnav" href="#">Label Most Likely</a>
    <a class="subnav" href="#">Label Most Confusing</a>
<a class="subnav" href="#">Label Least Likely</a>
<a class="subnav active" href="#">Label Randomly</a>
</div>*/
