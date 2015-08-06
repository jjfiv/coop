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
                   onChange={evt => this.handleChange(evt)} />
        </span>
    }
}
IntegerInput.defaultProps = {
    start: 0,
    onChange: function(val) { },
    min: 0,
    max: 2 << 30
};

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
SelectWidget.propTypes = {
    opts: React.PropTypes.objectOf(React.PropTypes.string).isRequired,
    selected: React.PropTypes.string.isRequired,
    onChange: React.PropTypes.func.isRequired
};
