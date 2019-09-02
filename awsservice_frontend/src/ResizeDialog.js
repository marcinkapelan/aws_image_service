import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min';
import React, { Component } from 'react';
import { Button, Modal, ModalHeader, ModalBody, ModalFooter, Input, InputGroup, InputGroupAddon } from 'reactstrap';

class ResizeDialog extends Component {

    constructor(props) {
        super(props);
        this.state = {
            modal: true,
            width: 100,
            height: 100
        }
    };

    widthChangeHandler = (e) => {
        this.setState({
            width: e.target.value
        })
    };

    heightChangeHandler = (e) => {
        this.setState({
            height: e.target.value
        })
    }

    okClickHandler = () => {
        this.props.onOkClicked(this.state.width, this.state.height);
        this.props.toggle();
    }

    render() {
        return (
            <div>
                <Modal isOpen={this.props.open} toggle={this.props.toggle}>
                    <ModalHeader toggle={this.props.toggle}>Resize settings</ModalHeader>
                    <ModalBody>
                        Width:
                        <InputGroup>
                            <Input defaultValue={this.state.width} min={0} max={10000} onChange={this.widthChangeHandler} type="number" step="1" />
                            <InputGroupAddon addonType="append">%</InputGroupAddon>
                        </InputGroup>
                        Height:
                        <InputGroup>
                            <Input defaultValue={this.state.height} min={0} max={10000} onChange={this.heightChangeHandler}  type="number" step="1" />
                            <InputGroupAddon addonType="append">%</InputGroupAddon>
                        </InputGroup>
                    </ModalBody>
                    <ModalFooter>
                        <Button color="primary" onClick={this.okClickHandler}>OK</Button>{' '}
                        <Button color="secondary" onClick={this.props.toggle}>Cancel</Button>
                    </ModalFooter>
                </Modal>
            </div>
        )
    }
}

export default ResizeDialog