/**
 * Usage: <Button disabled={true,false} onClick={whatFn} label={text label} />
 */
var Button = React.createClass({
    render: function() {
        return <input
            disabled={this.props.disabled}
            type={"button"}
            onClick={this.props.onClick}
            value={this.props.label}
            />;
    }
});
