require 'asciidoctor/extensions'
require 'open3'

Asciidoctor::Extensions.register do
  block do
    named :d2
    on_context :listing
    process do |parent, reader, attrs|
      d2_code = reader.source
      d2_path = (attrs['d2-executable'] || '/opt/homebrew/bin/d2').to_s
      svg_output, status = Open3.capture2(d2_path, '-s', '-', stdin_data: d2_code)
      if status.success?
        create_block parent, :pass, %(<div class="d2-diagram">#{svg_output}</div>), attrs, subs: nil
      else
        logger.error "Failed to generate D2 diagram: #{status.exitstatus}"
        nil
      end
    end
  end
end
