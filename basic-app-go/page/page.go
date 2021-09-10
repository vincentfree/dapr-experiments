package page

import "io/ioutil"

type Page struct {
	Title string
	Body  []byte
}

var (
	pages []*Page
)

func (p *Page) Save() error {
	fileName := p.Title + ".txt"
	pages = append(pages, p)
	return ioutil.WriteFile(fileName, p.Body, 0600)
}

func LoadPage(title string) (*Page, error) {
	fileName := title + ".txt"
	body, err := ioutil.ReadFile(fileName)
	if err != nil {
		return nil, err
	}
	return &Page{Title: title, Body: body}, nil
}
